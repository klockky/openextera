package com.exteragram.messenger.export.api;

import org.telegram.messenger.BuildVars;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

public final class ExportRequests {

    private ExportRequests() {
    }

    public static class InitTakeoutSession extends TLObject {
        public static final int constructor = 0x8ef3eab0;

        public int flags;
        public boolean contacts;
        public boolean message_users;
        public boolean message_chats;
        public boolean message_megagroups;
        public boolean message_channels;
        public boolean files;
        public long file_max_size;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Takeout.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = contacts ? (flags | 1) : (flags & ~1);
            flags = message_users ? (flags | 2) : (flags & ~2);
            flags = message_chats ? (flags | 4) : (flags & ~4);
            flags = message_megagroups ? (flags | 8) : (flags & ~8);
            flags = message_channels ? (flags | 16) : (flags & ~16);
            flags = files ? (flags | 32) : (flags & ~32);
            stream.writeInt32(flags);
            if (files) {
                stream.writeInt64(file_max_size);
            }
        }
    }

    public static class FinishTakeoutSession extends TLObject {
        public static int constructor = 0x1d2652ee;

        public int flags;
        public boolean success;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = success ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
        }
    }

    public static class Takeout extends TLObject {
        public long id;

        public static Takeout TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            Takeout result = new Takeout();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }
    }

    public static class InvokeWithTakeoutWrapper extends TLObject {
        public static final int constructor = 0xaca9fd2e;

        public long takeout_id;
        public TLObject query;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            switch (constructor) {
                case TLRPC.TL_messages_messages_layer215.constructor:
                case TLRPC.TL_messages_channelMessages.constructor:
                case TLRPC.TL_messages_messagesSlice.constructor:
                    return TLRPC.messages_Messages.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_photos_photos.constructor:
                case TLRPC.TL_photos_photosSlice.constructor:
                    return TLRPC.photos_Photos.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_boolTrue.constructor:
                case TLRPC.TL_boolFalse.constructor:
                    return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_messages_chatsSlice.constructor:
                case TLRPC.TL_messages_chats.constructor:
                    return TLRPC.messages_Chats.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_contacts_topPeersDisabled.constructor:
                case TLRPC.TL_contacts_topPeersNotModified.constructor:
                case TLRPC.TL_contacts_topPeers.constructor:
                    return TLRPC.contacts_TopPeers.TLdeserialize(stream, constructor, exception);
                case TL_account.webAuthorizations.constructor:
                    return TL_account.webAuthorizations.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_upload_file.constructor:
                    return TLRPC.upload_File.TLdeserialize(stream, constructor, exception);
                case TLRPC.TL_messages_dialogs.constructor:
                case TLRPC.TL_messages_dialogsSlice.constructor:
                    return TLRPC.messages_Dialogs.TLdeserialize(stream, constructor, exception);
                case Vector.constructor: {
                    Vector<?> vector;
                    if (query instanceof getSplitRanges) {
                        vector = new Vector<>(TLRPC.TL_messageRange::TLdeserialize);
                    } else if (query instanceof TL_contacts_getSaved) {
                        vector = new Vector<>(SavedContact::TLdeserialize);
                    } else if (query instanceof TLRPC.TL_messages_getCustomEmojiDocuments) {
                        vector = new Vector<>(TLRPC.Document::TLdeserialize);
                    } else {
                        vector = null;
                    }
                    if (query != null) {
                        query.freeResources();
                    }
                    if (vector == null) {
                        if (BuildVars.DEBUG_VERSION) {
                            throw new IllegalStateException("unable to deserialize vector with query: " + query);
                        }
                        return null;
                    }
                    vector.readParams(stream, exception);
                    return vector;
                }
                case TLRPC.TL_users_userFull.constructor:
                    return TLRPC.TL_users_userFull.TLdeserialize(stream, constructor, exception);
                case TL_account.authorizations.constructor:
                    return TL_account.authorizations.TLdeserialize(stream, constructor, exception);
                case TL_stories.TL_stories_stories.constructor:
                    return TL_stories.TL_stories_stories.TLdeserialize(stream, constructor, exception);
                default:
                    throw new IllegalArgumentException("cannot deserialize response with constructor: 0x" + Integer.toHexString(constructor));
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(takeout_id);
            if (query instanceof TL_contacts_getSaved || query instanceof getSplitRanges || query instanceof TLRPC.TL_messages_getCustomEmojiDocuments) {
                query.disableFree = true;
            }
            query.serializeToStream(stream);
        }
    }

    public static class InvokeWithMessagesRange extends TLObject {
        public static final int constructor = 0x365275f2;

        public TLRPC.TL_messageRange range;
        public TLObject query;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            TLObject result;
            switch (constructor) {
                case TLRPC.TL_messages_dialogs.constructor:
                    result = new TLRPC.TL_messages_dialogs();
                    break;
                case TLRPC.TL_messages_getHistory.constructor:
                    result = new TLRPC.TL_messages_getHistory();
                    break;
                case TLRPC.TL_messages_dialogsSlice.constructor:
                    result = new TLRPC.TL_messages_dialogsSlice();
                    break;
                default:
                    throw new RuntimeException("unknown constructor: " + constructor);
            }
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            range.serializeToStream(stream);
            query.serializeToStream(stream);
        }
    }

    public static class getSplitRanges extends TLObject {
        public static final int constructor = 0x1cff7e08;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            Vector<TLRPC.TL_messageRange> vector = new Vector<>(TLRPC.TL_messageRange::TLdeserialize);
            vector.readParams(stream, exception);
            return vector;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getLeftChannels extends TLObject {
        public static int constructor = 0x8341ecc0;

        public int offset;

        @Override
        public TLRPC.messages_Chats deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.messages_Chats.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
        }
    }

    public static class TL_contacts_getSaved extends TLObject {
        public static final int constructor = 0x82f1e39f;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            Vector<SavedContact> vector = new Vector<>(SavedContact::TLdeserialize);
            vector.readParams(stream, exception);
            return vector;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class SavedContact extends TLObject {
        public String phone;
        public String first_name;
        public String last_name;
        public int date;

        public static SavedContact TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            SavedContact result = new SavedContact();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            phone = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            date = stream.readInt32(exception);
        }
    }

    public static class TL_inputTakeoutFileLocation extends TLRPC.InputFileLocation {
        public static final int constructor = 0x29be5899;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
}
