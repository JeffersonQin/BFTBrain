package com.gbft.framework.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class RandomDataStream extends InputStream {
    private Random random = new Random();

    private int size = 0;

    public RandomDataStream(int size) {
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        if (size > 0) {
            size --;
            return random.nextInt(256);
        }
        return -1;
    }
}
