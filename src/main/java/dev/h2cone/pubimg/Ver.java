package dev.h2cone.pubimg;

import static picocli.CommandLine.IVersionProvider;

/**
 * Version Provider
 *
 * @author h2cone
 */
public class Ver implements IVersionProvider {
    static final String version;

    static {
        version = System.getProperty("pubimgVersion");
    }

    @Override
    public String[] getVersion() throws Exception {
        return new String[]{version};
    }
}
