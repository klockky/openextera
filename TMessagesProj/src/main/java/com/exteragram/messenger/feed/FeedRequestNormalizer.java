package com.exteragram.messenger.feed;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewrites synthetic feed message ids (and the peers they belong to) in outgoing requests
 * back to the real channel message ids.
 */
public abstract class FeedRequestNormalizer {

    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final ClassMetadata EMPTY_METADATA = new ClassMetadata(null, null, null, null, EMPTY_FIELDS);
    private static final ConcurrentHashMap<Class<?>, ClassMetadata> metadataCache = new ConcurrentHashMap<>();

    public static TLObject normalize(int currentAccount, TLObject request) {
        if (request == null) {
            return request;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null || feedController.hasNoSyntheticIds() || !request.getClass().getName().startsWith("org.telegram.tgnet.")) {
            return request;
        }
        ClassMetadata metadata = getMetadata(request);
        if (metadata.messageIdFields.length != 0 || metadata.invoiceField != null) {
            normalizeMessageIds(currentAccount, feedController, request, metadata);
            normalizeInvoice(currentAccount, feedController, getFieldValue(metadata.invoiceField, request));
        }
        return request;
    }

    private static ClassMetadata getMetadata(Object object) {
        if (object == null) {
            return EMPTY_METADATA;
        }
        return metadataCache.computeIfAbsent(object.getClass(), FeedRequestNormalizer::buildMetadata);
    }

    private static ClassMetadata buildMetadata(Class<?> clazz) {
        Field[] fields;
        try {
            fields = clazz.getFields();
        } catch (Exception e) {
            reportFailure("fields of " + clazz.getName(), e);
            fields = EMPTY_FIELDS;
        }
        Field fromPeerField = null;
        Field peerField = null;
        Field channelField = null;
        Field invoiceField = null;
        ArrayList<Field> messageIdFields = null;
        for (Field field : fields) {
            String name = field.getName();
            if ("from_peer".equals(name) && fromPeerField == null) {
                fromPeerField = field;
            } else if ("peer".equals(name) && peerField == null) {
                peerField = field;
            } else if ("channel".equals(name) && channelField == null) {
                channelField = field;
            } else if ("invoice".equals(name) && invoiceField == null) {
                invoiceField = field;
            }
            if (isMessageIdField(field)) {
                if (messageIdFields == null) {
                    messageIdFields = new ArrayList<>();
                }
                messageIdFields.add(field);
            }
        }
        return new ClassMetadata(
            fromPeerField != null ? fromPeerField : peerField,
            peerField,
            channelField,
            invoiceField,
            messageIdFields != null ? messageIdFields.toArray(new Field[0]) : EMPTY_FIELDS
        );
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object object) {
        normalizeMessageIds(currentAccount, feedController, object, getMetadata(object));
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object object, ClassMetadata metadata) {
        Field requestPeerField = metadata.requestPeerField;
        long dialogId = getDialogId(requestPeerField, object);
        if (dialogId == 0) {
            dialogId = getDialogId(metadata.peerField, object);
        }
        if (dialogId == 0) {
            dialogId = getChannelDialogId(metadata.channelField, object);
        }
        long resolvedDialogId = normalizeMessageIdFields(feedController, object, metadata);
        if (resolvedDialogId == 0 || resolvedDialogId == dialogId) {
            return;
        }
        if (requestPeerField != null) {
            setInputPeer(currentAccount, requestPeerField, object, resolvedDialogId);
        } else if (metadata.channelField != null) {
            setInputChannel(currentAccount, metadata.channelField, object, resolvedDialogId);
        }
    }

    private static long normalizeMessageIdFields(FeedController feedController, Object object, ClassMetadata metadata) {
        if (object == null) {
            return 0;
        }
        long dialogId = 0;
        for (Field field : metadata.messageIdFields) {
            dialogId = mergeResolvedDialogIds(dialogId, normalizeMessageIdField(feedController, object, field));
        }
        return dialogId;
    }

    private static boolean isMessageIdField(Field field) {
        if (field == null || Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        String name = field.getName();
        return "id".equals(name) || "msg_id".equals(name) || name.endsWith("_msg_id");
    }

    private static void normalizeInvoice(int currentAccount, FeedController feedController, Object invoice) {
        if (invoice instanceof TLRPC.TL_inputInvoiceMessage) {
            normalizeMessageIds(currentAccount, feedController, invoice);
        }
    }

    @SuppressWarnings("unchecked")
    private static long normalizeMessageIdField(FeedController feedController, Object object, Field field) {
        long dialogId = 0;
        try {
            Object value = field.get(object);
            if (value instanceof Integer) {
                int id = (Integer) value;
                long realDialogId = feedController.resolveRealDialogId(id);
                if (realDialogId != 0) {
                    field.setInt(object, feedController.resolveRealMessageId(realDialogId, id));
                    return realDialogId;
                }
            } else if (value instanceof ArrayList) {
                ArrayList<Object> list = (ArrayList<Object>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (!(item instanceof Integer)) {
                        continue;
                    }
                    int id = (Integer) item;
                    long realDialogId = feedController.resolveRealDialogId(id);
                    if (realDialogId != 0) {
                        setListInteger(list, i, feedController.resolveRealMessageId(realDialogId, id));
                        dialogId = mergeResolvedDialogIds(dialogId, realDialogId);
                    }
                }
                return dialogId;
            }
            return 0;
        } catch (Exception e) {
            reportFailure("message id field " + field.getName(), e);
            return dialogId;
        }
    }

    private static void setListInteger(List<Object> list, int index, int value) {
        list.set(index, value);
    }

    private static long mergeResolvedDialogIds(long current, long resolved) {
        if (current == 0) {
            return resolved;
        }
        if (resolved == 0 || current == resolved) {
            return current;
        }
        return 0;
    }

    private static void setInputPeer(int currentAccount, Field field, Object object, long dialogId) {
        if (currentAccount < 0) {
            return;
        }
        try {
            TLRPC.InputPeer peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (peer != null) {
                field.set(object, peer);
            }
        } catch (Exception e) {
            reportFailure("peer field " + field.getName(), e);
        }
    }

    private static void setInputChannel(int currentAccount, Field field, Object object, long dialogId) {
        if (currentAccount < 0 || dialogId >= 0) {
            return;
        }
        try {
            TLRPC.InputChannel channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
            if (channel != null) {
                field.set(object, channel);
            }
        } catch (Exception e) {
            reportFailure("channel field " + field.getName(), e);
        }
    }

    private static long getDialogId(Field field, Object object) {
        if (field == null) {
            return 0;
        }
        try {
            Object value = field.get(object);
            if (value instanceof TLRPC.InputPeer) {
                return DialogObject.getPeerDialogId((TLRPC.InputPeer) value);
            }
        } catch (Exception e) {
            reportFailure("peer field " + field.getName(), e);
        }
        return 0;
    }

    private static long getChannelDialogId(Field field, Object object) {
        Object value = getFieldValue(field, object);
        if (value instanceof TLRPC.InputChannel) {
            return getInputChannelDialogId((TLRPC.InputChannel) value);
        }
        return 0;
    }

    private static long getInputChannelDialogId(TLRPC.InputChannel channel) {
        if (channel == null || channel.channel_id == 0) {
            return 0;
        }
        return -channel.channel_id;
    }

    private static Object getFieldValue(Field field, Object object) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(object);
        } catch (Exception e) {
            reportFailure("field " + field.getName(), e);
            return null;
        }
    }

    private static void reportFailure(String what, Exception e) {
        FileLog.e("FeedRequestNormalizer failed to normalize " + what, e);
    }

    private static final class ClassMetadata {
        final Field requestPeerField;
        final Field peerField;
        final Field channelField;
        final Field invoiceField;
        final Field[] messageIdFields;

        ClassMetadata(Field requestPeerField, Field peerField, Field channelField, Field invoiceField, Field[] messageIdFields) {
            this.requestPeerField = requestPeerField;
            this.peerField = peerField;
            this.channelField = channelField;
            this.invoiceField = invoiceField;
            this.messageIdFields = messageIdFields;
        }
    }
}
