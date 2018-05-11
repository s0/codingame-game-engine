package com.codingame.gameengine.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.codejargon.feather.Feather;

public class RefereeMain {
    
    private static Feather feather;
    
    private static boolean inProduction = false;
    
    public static boolean isInProduction() {
        return inProduction;
    }

    public static void main(String[] args) throws InstantiationException, IllegalAccessException {
        inProduction = true;
        InputStream in = System.in;
        PrintStream out = System.out;
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // Do nothing.
            }
        }));
        System.setIn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new RuntimeException("Impossible to read from the referee");
            }
        });
        start(in, out);
    }

    @SuppressWarnings("unchecked")
    public static void start(InputStream is, PrintStream out) {
        feather = Feather.with(new GameEngineModule());
        
        GameManager<AbstractPlayer> gameManager = feather.instance(GameManager.class);
        
        gameManager.start(is, out);
    }
    
    public static Feather feather() {
        return feather;
    }
}