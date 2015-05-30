
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;

/**
 * Base class for unit tests providing logging and random seed setup.
 */
public abstract class TestSupport {

    private static boolean reportedSeed;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected Random random;

    @BeforeClass
    @Parameters({ "randomSeed" })
    public void seedRandom(String randomSeed) {
        this.random = getRandom(randomSeed);
    }

    public static Random getRandom(String randomSeed) {
        long seed;
        try {
            seed = Long.parseLong(randomSeed);
        } catch (NumberFormatException e) {
            seed = System.currentTimeMillis();
        }
        if (!reportedSeed) {
            reportedSeed = true;
            LoggerFactory.getLogger(TestSupport.class).info("test seed = " + seed);
        }
        return new Random(seed);
    }

    /**
     * Read some file.
     */
    protected byte[] readResource(File file) {
        try {
            return this.readResource(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("can't URL'ify file: " + file);
        }
    }

    /**
     * Read some classpath resource.
     */
    protected byte[] readResource(String path) {
        final URL url = this.getClass().getResource(path);
        if (url == null)
            throw new RuntimeException("can't find resource `" + path + "'");
        return this.readResource(url);
    }

    /**
     * Read some URL resource.
     */
    protected byte[] readResource(URL url) {
        InputStream input = null;
        try {
            input = url.openStream();
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            for (int r; (r = input.read(buf)) != -1; )
                buffer.write(buf, 0, r);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("error reading from " + url, e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Read some file in as a UTF-8 encoded string.
     */
    protected String readResourceAsString(File file) {
        return new String(this.readResource(file), Charset.forName("UTF-8"));
    }

    /**
     * Read some classpath resource in as a UTF-8 encoded string.
     */
    protected String readResourceAsString(String path) {
        return new String(this.readResource(path), Charset.forName("UTF-8"));
    }

    /**
     * Read some URL resource in as a UTF-8 encoded string.
     */
    protected String readResourceAsString(URL url) {
        return new String(this.readResource(url), Charset.forName("UTF-8"));
    }
}

