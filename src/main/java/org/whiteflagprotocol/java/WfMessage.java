/*
 * Whiteflag Java Library
 */
package org.whiteflagprotocol.java;

import java.util.Set;
import java.util.Map;
import java.security.interfaces.ECPublicKey;
import java.util.HashMap;

/* Required Whiteflag core and util classes */
import org.whiteflagprotocol.java.core.WfBinaryBuffer;
import org.whiteflagprotocol.java.core.WfCoreException;
import org.whiteflagprotocol.java.core.WfMessageCore;
import org.whiteflagprotocol.java.core.WfMessageCreator;
import org.whiteflagprotocol.java.core.WfMessageType;
import org.whiteflagprotocol.java.crypto.WfCipher;
import org.whiteflagprotocol.java.crypto.WfEncryptionKey;
import org.whiteflagprotocol.java.crypto.WfEncryptionMethod;
import org.whiteflagprotocol.java.crypto.WfCryptoException;
import org.whiteflagprotocol.java.util.WfJsonMessage;
import org.whiteflagprotocol.java.util.WfUtilException;

/* Required error types */
import static org.whiteflagprotocol.java.WfException.ErrorType.WF_FORMAT_ERROR;
import static org.whiteflagprotocol.java.WfException.ErrorType.WF_CRYPTO_ERROR;

/**
 * Whiteflag message class
 * 
 * <p> This is a class representing a Whiteflag message. It contains all
 * methods to handle a Whiteflag message, e.g. to encode, decode, etc. It also
 * provides static factory methods to create Whiteflag messages in different
 * ways from various data.
 * 
 * A Whiteflag message is put on a blockchain by embedding it in a transaction
 * in encoded, and possibly also encrypted, form. The blockchain account of the
 * message originator is virtually represented by a {@link WfBlockchainAccount}
 * object. 
 * 
 * @wfver v1-draft.6
 * 
 * @since 1.0
 */
public class WfMessage extends WfMessageCore {

    /* PROPERTIES */

    /* Constants */
    private static final String METAKEY_ORIGINATOR = "originatorAddress";
    private static final String METAKEY_RECIPIENT = "recipientAddress";
    private static final String FIELD_ENCRYPTIONINDICATOR = "EncryptionIndicator";

    /* Metadata */
    private Map<String, String> metadata = new HashMap<>();

    /* Originator and Recipient */
    private WfBlockchainAccount originator;
    private WfBlockchainAccount recipient;

    /* Encryption */
    private WfEncryptionMethod encryptionMethod;
    private byte[] initialisationVector;        //TODO: Expose iv

    /* Cached messge representations */
    private WfBinaryBuffer cachedMsg = WfBinaryBuffer.create();
    private String cachedSerializedMsg = null;

    /* CONSTRUCTORS */

    /**
     * Creates a Whiteflag message from a core message by calling the super constructor
     * @since 1.1
     * @param coreMsg the {@link WfMessageCore} core message
     */
    private WfMessage(final WfMessageCore coreMsg) {
        super(coreMsg);
    }

    /**
     * Creates a Whiteflag message from a decoded core message by calling the super constructor
     * @since 1.1
     * @param coreMsg the {@link WfMessageCore} core message
     * @param cachedMsg the {@link WfBinaryBuffer} with the source binary encoded message to be preserved
     */
    private WfMessage(final WfMessageCore coreMsg, final WfBinaryBuffer cachedMsg) {
        super(coreMsg);
        this.cachedMsg = cachedMsg.markComplete();
    }

    /**
     * Creates a Whiteflag message from a deserialized core message by calling the super constructor
     * @since 1.1
     * @param coreMsg the {@link WfMessageCore} core message
     * @param serializedMsg the source serialized message to be preserved
     */
    private WfMessage(final WfMessageCore coreMsg, final String serializedMsg) {
        super(coreMsg);
        this.cachedSerializedMsg = serializedMsg;
    }

    /* STATIC FACTORY METHODS */

    /**
     * Creates a new empty Whiteflag message object of the specified type
     * @param messageCode a string with the code indicating the message type to be created
     * @return a new {@link WfMessage} Whiteflag message
     * @throws WfException if the message cannot be created
     */
    public static final WfMessage create(final String messageCode) throws WfException {
        WfMessageCore coreMsg;
        try {
            coreMsg = new WfMessageCreator().type(WfMessageType.fromCode(messageCode)).create();
        } catch (WfCoreException e) {
            throw new WfException("Cannot create new message of type " + messageCode + ": " + e.getMessage(), WF_FORMAT_ERROR);
        }
        return new WfMessage(coreMsg);
    }

    /**
     * Copies a Whiteflag message into new Whiteflag message object, without metadata
     * @param message the message to be copied
     * @return a {@link WfMessage} Whiteflag message
     */
    public static final WfMessage copy(final WfMessage message) {
        return new WfMessage(message);
    }

    /**
     * Clones a Whiteflag message into new Whiteflag message object, including metadata
     * @param message the message to be copied
     * @return a {@link WfMessage} Whiteflag message
     */
    public static final WfMessage clone(final WfMessage message) {
        WfMessage newMessage = copy(message);
        for (String key : message.getMetadataKeys()) {
            newMessage.addMetadata(key, message.getMetadata(key));
        }
        return newMessage;
    }

    /**
     * Creates a new Whiteflag message object from a serialized message
     * @since 1.1
     * @param serializedMsg the uncompressed serialized message
     * @return a {@link WfMessage} Whiteflag message
     * @throws WfException if the serialization of the message is invalid
     */
    public static final WfMessage deserialize(final String serializedMsg) throws WfException {
        WfMessageCore coreMsg;
        try {
            coreMsg = new WfMessageCreator().deserialize(serializedMsg).create();
        } catch (WfCoreException e) {
            throw new WfException("Cannot deserialize message: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        return new WfMessage(coreMsg, serializedMsg);
    }

    /**
     * Creates a new Whiteflag message object from a serialized JSON message
     * @param jsonMessage the serialized JSON message
     * @return a {@link WfMessage} Whiteflag message
     * @throws WfException if the serialization of the message is invalid
     */
    public static final WfMessage deserializeJson(final String jsonMessage) throws WfException {
        // Deserialize JSON string
        WfJsonMessage jsonMsg;
        try {
            jsonMsg = WfJsonMessage.create(jsonMessage);
        } catch (WfUtilException e) {
            throw new WfException("Cannot deserialize JSON message: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        // Create message core with header and body fieldname-to-value mappings
        WfMessageCore coreMsg;
        try {
            coreMsg = new WfMessageCreator().map(jsonMsg.getHeader(), jsonMsg.getBody()).create();
        } catch (WfCoreException e) {
            throw new WfException("Cannot deserialize JSON message: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        // Create message and add metadata
        WfMessage message = new WfMessage(coreMsg);
        message.setMetadata(jsonMsg.getMetadata());
        return message;
    }

    /**
     * Creates a new Whiteflag message from a hexadecimal string represaentation of an encoded message
     * @since 1.1
     * @param hexMessage a hexadecimal string representation of the encoded message
     * @return a new {@link WfMessage} Whiteflag message
     * @throws WfException if the message cannot be decoded
     */
    public static final WfMessage decode(final String hexMessage) throws WfException {
        return decode(WfBinaryBuffer.fromHexString(hexMessage));
    }

    /**
     * Creates a new Whiteflag message from a byte array with an binary encoded message
     * @since 1.1
     * @param binMessage a byte array with the binary encoded message
     * @return a new {@link WfMessage} Whiteflag message
     * @throws WfException if the message cannot be decoded
     */
    public static final WfMessage decode(final byte[] binMessage) throws WfException {
        return decode(WfBinaryBuffer.fromByteArray(binMessage));
    }

    /**
     * Creates a new Whiteflag message from a binary buffer
     * @since 1.1
     * @param cachedMsg a binary buffer with the encoded message
     * @return a new {@link WfMessage} Whiteflag message
     * @throws WfException if the message cannot be decoded
     */
    public static final WfMessage decode(final WfBinaryBuffer cachedMsg) throws WfException {
        WfMessageCore coreMsg;
        try {
            coreMsg = new WfMessageCreator().decode(cachedMsg).create();
        } catch (WfCoreException e) {
            throw new WfException("Cannot decode message: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        return new WfMessage(coreMsg, cachedMsg);
    }

    /**
     * Creates a new Whiteflag message object from field values
     * @param fieldValues a string array with the values for the message fields
     * @return a {@link WfMessage} Whiteflag message
     * @throws WfException if any of the provided values is invalid
     */
    public static final WfMessage compile(final String[] fieldValues) throws WfException {
        WfMessageCore coreMsg;
        try {
            coreMsg = new WfMessageCreator().compile(fieldValues).create();
        } catch (WfCoreException e) {
            throw new WfException("Cannot compile message: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        return new WfMessage(coreMsg);
    }

    /* PUBLIC METHODS: Getters & Setters */

    /**
     * Adds metadata to the Whiteflag message if not already existing
     * @param key a string with the metadata key
     * @param value a string with the metadata value
     * @return null if successful, otherwise the value of the already existing key
     */
    public String addMetadata(final String key, final String value) {
        return metadata.putIfAbsent(key, value);
    }

    /**
     * Returns the requested metadata value of the Whiteflag message
     * @param key a string with the metadata key
     * @return the value associated with the requested metadata key
     */
    public String getMetadata(final String key) {
        return metadata.get(key);
    }

    /**
     * Returns metadata keys of the Whiteflag message
     * @return a string set with all metadata keys
     */
    public Set<String> getMetadataKeys() {
        return metadata.keySet();
    }

    /**
     * Sets the originator sending this message and adds its blockchain address to the metadata
     * @since 1.1
     * @param originator the {@link WfBlockchainAccount} of the originator sending this message
     * @return null if address newly added to metadata, otherwise the existing value that was replaced
     */
    public final String setOriginator(WfBlockchainAccount originator) {
        this.originator = originator;
        return metadata.put(METAKEY_ORIGINATOR, originator.getAddressString());
    }

    /**
     * Gets the originator of this message
     * @since 1.1
     * @return the {@link WfBlockchainAccount} of the originator
     */
    public final WfBlockchainAccount getOriginator() {
        return this.originator;
    }

    /**
     * Sets the intended recipient of this message (if any) and adds its blockchain address to the metadata
     * @since 1.1
     * @param recipient the {@link WfBlockchainAccount} of the recipient of this message
     * @return null if address newly added to metadata, otherwise the existing value that was replaced
     */
    public final String setRecipient(WfBlockchainAccount recipient) {
        this.recipient = originator;
        return metadata.put(METAKEY_RECIPIENT, recipient.getAddressString());
    }

    /**
     * Gets the recipient of this message
     * @since 1.1
     * @return the {@link WfBlockchainAccount} of the recipient
     */
    public final WfBlockchainAccount getRecipient() {
        return this.recipient;
    }

    /* PUBLIC METHODS: Operations */

    /**
     * Returns the cached serialized message, or else it serialzes and caches Whiteflag message
     * @return the serialized message, i.e. the concatinated string of field values
     * @throws WfException if any of the field does not contain valid data
     */
    @Override
    public final String serialize() throws WfException {
        if (this.cachedSerializedMsg == null) {
            try {
                this.cachedSerializedMsg = super.serialize();
            } catch (WfCoreException e) {
                throw new WfException(e.getMessage(), WF_FORMAT_ERROR);
            }
        }
        return this.cachedSerializedMsg;
    }

    /**
     * Returns the cached encoded/encrypted message, or else it encodes/encrypts and caches Whiteflag message
     * @since 1.1
     * @return a byte array with the compressed binary encoded message
     * @throws WfException if any field does not contain valid data
     */
    @Override
    public final WfBinaryBuffer encode() throws WfException {
        /* Return encoded/encrypted message if already cached */
        if (Boolean.TRUE.equals(cachedMsg.isComplete())) return this.cachedMsg;

        /* Check encryption method */
        try {
            this.encryptionMethod = WfEncryptionMethod.fromFieldValue(header.get(FIELD_ENCRYPTIONINDICATOR));
        } catch (WfCryptoException e) {
            throw new WfException(e.getMessage(), WF_FORMAT_ERROR);
        }
        /* Encode message */
        WfBinaryBuffer encodedMsg;
        try {
            encodedMsg = super.encode();
        } catch (WfCoreException e) {
            throw new WfException(e.getMessage(), WF_FORMAT_ERROR);
        }
        /* Encrypt the encoded message */
        WfBinaryBuffer encryptedMsg;
        try {
            encryptedMsg = encrypt(encodedMsg);
        } catch (WfCryptoException e) {
            throw new WfException(e.getMessage(), WF_CRYPTO_ERROR);
        }
        /* Done. Cache and return the result */
        this.cachedMsg = encryptedMsg.markComplete();
        return encryptedMsg;
    }

    /**
     * Returns a byte array with the binary encoded message
     * @since 1.1
     * @return a byte array with the binary encoded message
     * @throws WfException if any field does not contain valid data
     */
    public final byte[] toByteArray() throws WfException {
        return this.encode().toByteArray();
    }

    /**
     * Returns a hexedimal string representation of the binary encoded message
     * @since 1.1
     * @return a hexadecimal string representation of the binary encoded
     * @throws WfException if any field does not contain valid data
     */
    public final String toHexString() throws WfException {
        return this.encode().toHexString();
    }

    /**
     * Returns the serialised JSON representation of the Whiteflag message
     * @return the serialised JSON representation
     * @throws WfException if the message cannot be serialised
     */
    public final String toJson() throws WfException {
        String jsonMsgStr;
        try {
            jsonMsgStr = new WfJsonMessage(metadata, header.toMap(), body.toMap()).toJson();
        } catch (WfUtilException e) {
            throw new WfException("Cannot serialize message into JSON string: " + e.getMessage(), WF_FORMAT_ERROR);
        }
        return jsonMsgStr;
    }

    /* PRIVATE METHODS */

    /**
     * Returns the requested metadata value of the Whiteflag message
     * @return the value of the requested metadata key
     */
    private final void setMetadata(final Map<String, String> metadata) {
        metadata.forEach(this.metadata::put);
    }

    /**
     * Encrypts the message
     * @since 1.1
     * @param encodedMessage a {@link WfBinaryBuffer} with an encoded message
     * @return a {@link WfBinaryBuffer} with the encrypted message
     * @throws IllegalStateException if originator and recipient of this message are unknown
     * @throws WfCryptoException if message cannot be encrypted
     */
    private final WfBinaryBuffer encrypt(WfBinaryBuffer encodedMsg) throws WfCryptoException {
        if (encryptionMethod == WfEncryptionMethod.NO_ENCRYPTION) return encodedMsg;

        /* Perform encryption */
        WfCipher cipher  = WfCipher.fromKey(getEncryptionKey());
        cipher.setContext(originator.getBinaryAddress());
        this.initialisationVector = cipher.getInitVector();

        //TODO: Get the correct part of the message
        return WfBinaryBuffer.fromByteArray(cipher.encrypt(encodedMsg.toByteArray()));
    }

    /**
     * Retrieves the encryption key from the key store
     * @param method the encryption method
     * @return the requested {@link WfEncryptionKey}
     * @throws IllegalStateException if originator and recipient of this message are unknown
     */
    private final WfEncryptionKey getEncryptionKey() {
        if (originator == null) throw new IllegalStateException("Cannot encrypt message if originator is not set");
        if (recipient == null) throw new IllegalStateException("Cannot encrypt message if recipient is not set");
        return new WfEncryptionKey("a1b2c3d4e5f6");  //TODO: implement key store
    }
}
