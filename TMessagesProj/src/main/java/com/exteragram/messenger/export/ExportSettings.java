package com.exteragram.messenger.export;

import com.exteragram.messenger.export.output.AbstractWriter;

import org.telegram.tgnet.TLRPC;

public class ExportSettings {

    public String path;
    public TLRPC.InputPeer singlePeer = new TLRPC.TL_inputPeerEmpty();
    public AbstractWriter.Format format = AbstractWriter.Format.Json;
    public int singlePeerFrom = 0;
    public int singlePeerTill = 0;
    public MediaSettings media = new MediaSettings();
    public int types = 32;

    public boolean onlySinglePeer() {
        return !(singlePeer instanceof TLRPC.TL_inputPeerEmpty);
    }

    public static class MediaSettings {

        public int type = 1;
        public long sizeLimit = 8 * 1024 * 1024;

        public boolean isEnabled() {
            return (type & 1) != 0
                || (type & 2) != 0
                || (type & 4) != 0
                || (type & 8) != 0
                || (type & 16) != 0
                || (type & 32) != 0
                || (type & 64) != 0;
        }
    }
}
