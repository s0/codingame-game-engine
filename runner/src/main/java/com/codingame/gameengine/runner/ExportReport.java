package com.codingame.gameengine.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

enum ExportStatus {
    SUCCESS, FAIL
}

enum ReportItemType {
    ERROR, WARNING, MISSING_MANDATORY_FILE, INFO
}

public class ExportReport {
    private List<ReportItem> reportItems = new ArrayList<>();
    private ExportStatus exportStatus = ExportStatus.SUCCESS;
    private String data;
    private Map<String, String> stubs = new HashMap<>();

    public List<ReportItem> getReportItems() {
        return reportItems;
    }

    public void setReportItems(List<ReportItem> reportItems) {
        this.reportItems = reportItems;
    }

    public ExportStatus getExportStatus() {
        return exportStatus;
    }

    public void setExportStatus(ExportStatus exportStatus) {
        this.exportStatus = exportStatus;
    }

    public void addItem(ReportItemType type, String message) {
        reportItems.add(new ReportItem(type, message));
        if (type == ReportItemType.ERROR || type == ReportItemType.MISSING_MANDATORY_FILE) {
            exportStatus = ExportStatus.FAIL;
        }
    }

    public void addItem(ReportItemType type, String message, String link) {
        reportItems.add(new ReportItem(type, message, link));
        if (type == ReportItemType.ERROR || type == ReportItemType.MISSING_MANDATORY_FILE) {
            exportStatus = ExportStatus.FAIL;
        }
    }

    public void merge(ExportReport exportReport) {
        for (ReportItem reportItem : exportReport.getReportItems()) {
            if (reportItem.getLink() == null)
                addItem(reportItem.getType(), reportItem.getMessage());
            else
                addItem(reportItem.getType(), reportItem.getMessage(), reportItem.getLink());
        }
        for (String stub : exportReport.getStubs().keySet()) {
            stubs.put(stub, exportReport.getStubs().get(stub));
        }
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Map<String, String> getStubs() {
        return stubs;
    }

    public void setStubs(Map<String, String> stubs) {
        this.stubs = stubs;
    }

    public boolean hasMandatoryFileMissing() {
        for (ReportItem reportItem : reportItems) {
            if (reportItem.getType() == ReportItemType.MISSING_MANDATORY_FILE) {
                return true;
            }
        }
        return false;
    }

    public void prettify() {
        for (ReportItem reportItem : reportItems) {
            if (reportItem.getType() == ReportItemType.MISSING_MANDATORY_FILE) {
                reportItem.setType(ReportItemType.ERROR);
            }
        }
    }
}

class ReportItem {
    private String message;
    private ReportItemType type;
    private String link;

    public ReportItem(ReportItemType type, String message) {
        super();
        this.message = message;
        this.type = type;
    }

    public ReportItem(ReportItemType type, String message, String link) {
        this(type, message);
        this.link = link;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ReportItemType getType() {
        return type;
    }

    public void setType(ReportItemType type) {
        this.type = type;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

}