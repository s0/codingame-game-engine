package com.codingame.gameengine.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceSupplier;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

class Renderer {

    private static final int MAX_LEAGUES = 50;
    private static final int MAX_PLAYERS = 8;

    public class MultipleResourceSupplier implements ResourceSupplier {

        private List<FileResourceManager> directories = new ArrayList<>();

        public void addDirectory(File directory) {
            FileResourceManager p = new FileResourceManager(directory);
            directories.add(p);
        }

        @Override
        public Resource getResource(HttpServerExchange exchange, String path) throws IOException {
            for (FileResourceManager dir : directories) {
                Resource resource = dir.getResource(path);
                if (resource != null) {
                    return resource;
                }
            }
            return null;
        }
    }

    private int port = 8080;

    public Renderer(int port) {
        this.port = port;
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { // some JVMs return null for empty directories
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    private static List<Path> exportViewToWorkingDir(String sourceFolder, Path targetFolder)
        throws IOException {
        List<Path> exportedPaths = new ArrayList<>();

        Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(sourceFolder);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (url == null) {
                continue;
            }

            if ("jar".equals(url.getProtocol())) {
                JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
                ZipFile jar = jarConnection.getJarFile();
                Enumeration<? extends ZipEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(sourceFolder)) {
                        continue;
                    }
                    String entryTail = name.substring(sourceFolder.length());

                    File f = new File(targetFolder + File.separator + entryTail);
                    if (entry.isDirectory()) {
                        f.mkdir();
                    } else {
                        Files.copy(jar.getInputStream(entry), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } else if ("file".equals(url.getProtocol())) {
                try {
                    String targetClassesFolder = "/target/classes/".replace('/', File.separatorChar) + sourceFolder;
                    String resourcesClassesFolder = "/src/main/resources/".replace('/', File.separatorChar)
                        + sourceFolder;

                    String targetPath = new File(url.toURI()).getAbsolutePath();
                    targetPath = targetPath.replace(targetClassesFolder, resourcesClassesFolder);

                    exportedPaths.add(new File(targetPath).toPath());
                    FileUtils.copyDirectory(new File(url.toURI()), targetFolder.toFile());
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Cannot copy files", e);
                }
            }
        }

        // copied version has a lower priority
        exportedPaths.add(targetFolder);

        return exportedPaths;
    }

    private static String hashAsset(Path asset) throws IOException {
        HashCode hash = com.google.common.io.Files.asByteSource(new File(asset.toUri()))
            .hash(Hashing.sha256());
        String newName = hash.toString() + "."
            + FilenameUtils.getExtension(asset.getFileName().toString());
        return newName;

    }

    private static void generateAssetsFile(Path tmpdir, String assetsPath) {
        boolean assetsNeedHashing = assetsPath != null;

        if (assetsNeedHashing) {
            tmpdir.resolve("hashed_assets").toFile().mkdirs();
        }
        File assets = tmpdir.resolve("assets.js").toFile();
        try (PrintWriter out = new PrintWriter(assets)) {
            JsonObject jsonAssets = new JsonObject();
            if (assetsNeedHashing) {
                jsonAssets.addProperty("baseUrl", assetsPath);
            }
            JsonObject images = new JsonObject();
            JsonArray sprites = new JsonArray();
            jsonAssets.add("images", images);
            jsonAssets.add("sprites", sprites);

            Path origAssetsPath = tmpdir.resolve("assets");
            try {
                Files.find(origAssetsPath, 100, (p, bfa) -> bfa.isRegularFile()).forEach(
                    f -> {
                        try {
                            if (isSpriteJson(f)) {
                                JsonParser parser = new JsonParser();
                                JsonElement jsonElement = parser.parse(new FileReader(f.toString()));
                                JsonObject jsonObject = jsonElement.getAsJsonObject();
                                String image = jsonObject.getAsJsonObject("meta").get("image").getAsString();
                                Path imagePath = origAssetsPath.resolve(image);

                                String jsonToWriteTo = null;
                                if (assetsNeedHashing) {
                                    String hashedImageName = hashAsset(imagePath);
                                    jsonObject.getAsJsonObject("meta").add("image", new JsonPrimitive(hashedImageName));
                                    String newName = hashAsset(f);
                                    jsonToWriteTo = tmpdir.resolve("hashed_assets").resolve(newName).toString();
                                    Files.createDirectories(tmpdir.resolve("hashed_assets"));
                                    sprites.add(newName);
                                } else {
                                    String relativeImagePath = tmpdir.relativize(imagePath).toString();
                                    jsonObject.getAsJsonObject("meta").add("image", new JsonPrimitive(relativeImagePath));
                                    jsonToWriteTo = f.toString();
                                    sprites.add(tmpdir.relativize(f).toString());
                                }
                                try (FileWriter writer = new FileWriter(jsonToWriteTo)) {
                                    Gson gson = new GsonBuilder().create();
                                    gson.toJson(jsonObject, writer);
                                }
                            } else {
                                if (assetsNeedHashing) {
                                    String newName = hashAsset(f);
                                    images.addProperty(origAssetsPath.relativize(f).toString(), newName);
                                    Files.copy(
                                        f, tmpdir.resolve("hashed_assets").resolve(newName),
                                        StandardCopyOption.REPLACE_EXISTING
                                    );
                                } else {
                                    images.addProperty(
                                        origAssetsPath.relativize(f).toString(),
                                        tmpdir.relativize(f).toString()
                                    );
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                );
            } catch (NoSuchFileException e) {
                System.out.println("Directory src/main/resources/view/assets not found.");
            }

            out.print("export const assets = ");
            out.println(jsonAssets.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isSpriteJson(Path f) {
        return "json".equals(FilenameUtils.getExtension(f.toString()));
    }

    public static List<Path> generateView(String jsonResult, String assetsPath) {
        List<Path> paths;

        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("codingame");
        deleteFolder(tmpdir.toFile());
        tmpdir.toFile().mkdirs();

        // Windows compatibility hack
        try {
            tmpdir = tmpdir.toRealPath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (jsonResult != null) {
            File game = tmpdir.resolve("game.json").toFile();
            try (PrintWriter out = new PrintWriter(game)) {
                out.println(jsonResult);
            } catch (IOException e) {
                throw new RuntimeException("Cannot generate the game file", e);
            }
        }

        try {
            paths = exportViewToWorkingDir("view", tmpdir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy resources", e);
        }

        // Depends on exportViewToWorkingDir
        generateAssetsFile(tmpdir, assetsPath);

        if (paths.size() == 0) {
            throw new RuntimeException("No resources folder found");
        }

        // Create empty demo.js if needed
        Path sourceFolderPath = new File(System.getProperty("user.dir")).toPath();
        File demoFile = sourceFolderPath.resolve("src/main/resources/view/demo.js").toFile();
        if (!demoFile.exists()) {
            try (PrintWriter out = new PrintWriter(demoFile)) {
                out.println("export const demo = null;");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return paths;
    }

    private ExportReport checkConfig(Path sourceFolderPath) throws IOException {
        ExportReport exportReport = new ExportReport();
        Path rootDir = sourceFolderPath.resolve("config");

        if (!rootDir.toFile().isDirectory()) {
            exportReport.addItem(ReportItemType.ERROR, "Missing config directory.");
            return exportReport;
        }

        //Check leagues
        boolean consecutiveLeague = true;
        int nbLeagues = 0;
        for (int i = 1; i < MAX_LEAGUES; i++) {
            if (!sourceFolderPath.resolve("config/level" + i).toFile().isDirectory()) {
                consecutiveLeague = false;
            } else {
                nbLeagues++;
                if (!consecutiveLeague) {
                    exportReport.addItem(ReportItemType.ERROR, "Folder level" + i + " must be consecutive with previous leagues");
                    return exportReport;
                }
            }
        }
        if (sourceFolderPath.resolve("config/level" + (MAX_LEAGUES + 1)).toFile().isDirectory()) {
            exportReport.addItem(ReportItemType.ERROR, "Too many leagues (>" + MAX_LEAGUES + ")");
            return exportReport;
        }

        boolean hasLeagues = nbLeagues != 0;

        //If there is no league, only check files in config/
        if (!hasLeagues) {
            //Check config.ini
            checkConfigIni(sourceFolderPath, exportReport, "");

            //Check stub
            checkStub(sourceFolderPath, exportReport, "");

            //Check statement
            checkStatement(sourceFolderPath, exportReport, "");

            //Check Boss
            checkBoss(sourceFolderPath, exportReport, "");

            //Check League popups
            checkLeaguePopups(sourceFolderPath, exportReport, "", false);
            return exportReport;
        } else {
            for (int i = 1; i <= nbLeagues; i++) {
                String filePath = "level" + i + "/";

                //Check config.ini
                ExportReport tmpReport = new ExportReport();
                if (!checkConfigIni(sourceFolderPath, tmpReport, filePath)) {
                    if (tmpReport.hasMandatoryFileMissing()) {
                        exportReport.addItem(ReportItemType.INFO, "config/" + filePath + ": Inherit config.ini file from config directory");
                        checkConfigIni(sourceFolderPath, exportReport, "");
                    } else {
                        exportReport.merge(tmpReport);
                    }
                }

                //Check stub
                tmpReport = new ExportReport();
                if (!checkStub(sourceFolderPath, tmpReport, filePath)) {
                    exportReport.addItem(ReportItemType.INFO, "config/" + filePath + ": Inherit stub.txt file from config directory.");
                    checkStub(sourceFolderPath, exportReport, "");
                } else {
                    exportReport.merge(tmpReport);
                }

                //Check statement
                tmpReport = new ExportReport();
                if (!checkStatement(sourceFolderPath, tmpReport, filePath)) {
                    exportReport.addItem(ReportItemType.INFO, "config/" + filePath + ": Inherit statement_en.html file from config directory.");
                    checkStatement(sourceFolderPath, exportReport, "");
                } else {
                    exportReport.merge(tmpReport);
                }

                //Check Boss
                tmpReport = new ExportReport();
                if (!checkBoss(sourceFolderPath, tmpReport, filePath)) {
                    exportReport.addItem(ReportItemType.INFO, "config/" + filePath + ": Inherit Boss.* file from config directory.");
                    checkBoss(sourceFolderPath, exportReport, "");
                } else {
                    exportReport.merge(tmpReport);
                }

                //Check league popups
                tmpReport = new ExportReport();
                if (!checkLeaguePopups(sourceFolderPath, tmpReport, filePath, true)) {
                    exportReport.addItem(ReportItemType.INFO, "config/" + filePath + ": Inherit Boss.* file from config directory.");
                    checkLeaguePopups(sourceFolderPath, exportReport, "", true);
                } else {
                    exportReport.merge(tmpReport);
                }
            }
        }

        exportReport.prettify();

        return exportReport;
    }

    private boolean checkLeaguePopups(Path sourceFolderPath, ExportReport exportReport, String filePath, boolean hasLeagues) throws IOException {
        String completeFilePath = "config/" + filePath + "welcome_en.html";
        File stubFile = sourceFolderPath.resolve(completeFilePath).toFile();
        if (!stubFile.isFile()) {
            //Displays warning only if leagues are present
            if (hasLeagues) {
                exportReport.addItem(
                    ReportItemType.WARNING, completeFilePath +
                        ": Missing welcome_en.html file."
                );
            }
            return false;
        } else {
            //Check if league popups images stated in html file exist
            FileInputStream welcomeInput = new FileInputStream(stubFile);
            String welcomeContent = "";
            int content;
            while ((content = welcomeInput.read()) != -1) {
                welcomeContent += ((char) content);
            }
            Matcher imageMatcher = Pattern.compile("<\\s*img [^\\>]*src\\s*=\\s*([\"\\'])(.*?)\\1").matcher(welcomeContent);
            List<String> imagesName = new ArrayList<>();

            while (imageMatcher.find()) {
                imagesName.add(imageMatcher.group(2));
            }
            welcomeInput.close();

            //Check if images exist in arborescence
            checkLeagueImages(sourceFolderPath, "config/" + filePath, imagesName);
            if (!filePath.isEmpty() && !imagesName.isEmpty())
                checkLeagueImages(sourceFolderPath, "config/", imagesName);

            //Remaining images in list do not exist 
            for (String imageName : imagesName) {
                exportReport.addItem(ReportItemType.WARNING, completeFilePath + ": File " + imageName + " is used but missing.");
            }

            return true;
        }
    }

    private void checkLeagueImages(Path sourceFolderPath, String filePath, List<String> imagesName) throws IOException {
        Files.list(sourceFolderPath.resolve(filePath)).forEach(p -> imagesName.remove(FilenameUtils.getName(p.toString())));
    }

    private boolean checkBoss(Path sourceFolderPath, ExportReport exportReport, String filePath) throws IOException {
        String completeFolderPath = "config/" + filePath;
        boolean hasBossFile = Files.list(sourceFolderPath.resolve(completeFolderPath)).filter(p -> {
            String fileName = FilenameUtils.getName(p.toString());
            Matcher bossMatcher = Pattern.compile("Boss\\..*").matcher(fileName);
            return p.toFile().isFile() && bossMatcher.matches();

        }).findFirst().isPresent();

        if (!hasBossFile) {
            exportReport.addItem(ReportItemType.MISSING_MANDATORY_FILE, "Missing " + completeFolderPath + "Boss.* file.");
        }

        return hasBossFile;
    }

    private boolean checkStatement(Path sourceFolderPath, ExportReport exportReport, String filePath) throws IOException {
        return existsMandatoryFile(sourceFolderPath, exportReport, filePath, "statement_en.html");
    }

    private boolean existsMandatoryFile(Path sourceFolderPath, ExportReport exportReport, String filePath, String filename) {
        String completeFilePath = "config/" + filePath + filename;
        File fileToCheck = sourceFolderPath.resolve(completeFilePath).toFile();
        if (!fileToCheck.isFile()) {
            exportReport.addItem(ReportItemType.MISSING_MANDATORY_FILE, "Missing " + completeFilePath + " file.");
            return false;
        }
        return true;
    }

    private boolean checkStub(Path sourceFolderPath, ExportReport exportReport, String filePath) throws IOException {
        String completeFilePath = "config/" + filePath + "stub.txt";
        File stubFile = sourceFolderPath.resolve(completeFilePath).toFile();
        if (!stubFile.isFile()) {
            exportReport.addItem(
                ReportItemType.WARNING, completeFilePath +
                    ": Missing stub.txt file.",
                "https://github.com/CodinGame/codingame-game-engine/blob/master/stubGeneratorSyntax.md"
            );

            return false;
        } else {
            FileInputStream stubInput = new FileInputStream(stubFile);
            String stubContent = "";
            int content;
            while ((content = stubInput.read()) != -1) {
                stubContent += ((char) content);
            }
            exportReport.getStubs().put(completeFilePath, stubContent);
            stubInput.close();

            return true;
        }
    }

    private boolean checkConfigIni(Path sourceFolderPath, ExportReport exportReport, String filePath) throws IOException {
        String completeFilePath = "config/" + filePath + "config.ini";

        boolean mandatoryFileIsPresent = existsMandatoryFile(sourceFolderPath, exportReport, filePath, "config.ini");
        if (!mandatoryFileIsPresent)
            return false;

        File configFile = sourceFolderPath.resolve(completeFilePath).toFile();
        FileInputStream configInput = new FileInputStream(configFile);
        Properties config = new Properties();
        config.load(configInput);
        if (!config.containsKey("title")) {
            exportReport.addItem(
                ReportItemType.ERROR, completeFilePath +
                    ": Missing title property in config.ini."
            );

            return false;
        }

        List<Integer> numberPlayerBounds = new ArrayList<>();
        for (String s : new String[] { "min", "max" }) {
            if (!config.containsKey(s + "_players")) {
                exportReport.addItem(
                    ReportItemType.ERROR, completeFilePath +
                        ": Missing " + s + "_players property in config.ini."
                );

                return false;
            }
            String playerStr = config.getProperty(s + "_players");
            try {
                numberPlayerBounds.add(Integer.parseInt(playerStr));
            } catch (Exception e) {
                exportReport.addItem(
                    ReportItemType.ERROR, completeFilePath +
                        ": " + s + "_players property is not an integer."
                );

                return false;
            }
        }

        if (numberPlayerBounds.get(0) <= 0) {
            exportReport.addItem(
                ReportItemType.ERROR, completeFilePath +
                    ": min_players property should be greater than 0."
            );

            return false;
        }
        if (numberPlayerBounds.get(0) > numberPlayerBounds.get(1)) {
            exportReport.addItem(
                ReportItemType.ERROR, completeFilePath +
                    ": max_players property should be greater or equal to min_players property."
            );

            return false;
        }
        if (numberPlayerBounds.get(1) > MAX_PLAYERS) {
            exportReport.addItem(
                ReportItemType.ERROR, completeFilePath +
                    ": max_players property should be lower or equal to " + MAX_PLAYERS + "."
            );

            return false;
        }
        configInput.close();
        return true;
    }

    private Path exportSourceCode(Path sourceFolderPath, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walkFileTree(
                sourceFolderPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relativePath = sourceFolderPath.relativize(file).toString();
                        if (relativePath.startsWith("config") || relativePath.startsWith("src") || relativePath.equals("pom.xml")) {
                            zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString().replace('\\', '/')));
                            Files.copy(file, zos);
                            zos.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                }
            );
        }

        return zipPath;
    }

    private void serveHTTP(List<Path> path) {
        System.out.println("http://localhost:" + port + "/test.html");

        MultipleResourceSupplier mrs = new MultipleResourceSupplier();
        for (Path p : path) {
            mrs.addDirectory(p.toFile());
            System.out.println("Exposed web server dir: " + p.toString());
        }

        Undertow server = Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(
                new DisableCacheHandler(
                    Handlers.path(new ResourceHandler(mrs).addWelcomeFiles("test.html"))
                        .addPrefixPath(
                            "/services/", new HttpHandler() {
                                @Override
                                public void handleRequest(HttpServerExchange exchange) throws Exception {
                                    Path sourceFolderPath = new File(System.getProperty("user.dir")).toPath();
                                    try {
                                        if (exchange.getRelativePath().equals("/export")) {
                                            Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("codingame");

                                            Path zipPath = tmpdir.resolve("source.zip");

                                            ExportReport exportReport = checkConfig(sourceFolderPath);
                                            if (exportReport.getExportStatus() == ExportStatus.SUCCESS) {
                                                byte[] data = Files.readAllBytes(exportSourceCode(sourceFolderPath, zipPath));
                                                exportReport.setData(Base64.getEncoder().encodeToString(data));
                                            }
                                            String jsonExportReport = new Gson().toJson(exportReport);
                                            exchange.getResponseSender().send(jsonExportReport);

                                        } else if (exchange.getRelativePath().equals("/init-config")) {
                                            if (!sourceFolderPath.resolve("config").toFile().isDirectory()) {
                                                sourceFolderPath.resolve("config").toFile().mkdir();
                                            }
                                            File configFile = sourceFolderPath.resolve("config/config.ini").toFile();
                                            if (!configFile.exists()) {
                                                configFile.createNewFile();
                                            }
                                            FileOutputStream configOutput = new FileOutputStream(configFile);
                                            Properties config = new Properties();

                                            exchange.getQueryParameters().forEach(
                                                (k, v) -> {
                                                    config.put(k, v.stream().collect(Collectors.joining(",")));
                                                }
                                            );

                                            config.store(configOutput, null);
                                            exchange.setStatusCode(StatusCodes.FOUND);
                                            exchange.getResponseHeaders().put(Headers.LOCATION, "/export.html");
                                            exchange.endExchange();
                                        } else if (exchange.getRelativePath().equals("/save-replay")) {
                                            Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("codingame");
                                            File demoFile = sourceFolderPath.resolve("src/main/resources/view/demo.js").toFile();
                                            File gameFile = tmpdir.resolve("game.json").toFile();

                                            try (PrintWriter out = new PrintWriter(demoFile)) {
                                                out.println("export const demo = ");

                                                List<String> lines = Files.readAllLines(gameFile.toPath());
                                                for (String line : lines) {
                                                    out.println(line);
                                                }
                                                out.println(";");
                                            }

                                            exchange.setStatusCode(StatusCodes.OK);
                                            exchange.endExchange();
                                        }
                                    } catch (Exception e) {
                                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                                        exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                                        exchange.getResponseSender().send(e.getMessage());
                                    }
                                }
                            }
                        )
                )
            )
            .build();
        server.start();
    }

    public void render(int playerCount, String jsonResult) {
        List<Path> paths = generateView(jsonResult, null);
        serveHTTP(paths);
    }
}
