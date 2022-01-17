/*
 * Whiteflag Java Library
 */
package org.whiteflagprotocol.java.crypto;

/* Whiteflag authentication methods */
import static org.whiteflagprotocol.java.crypto.WfAuthMethod.*;

/**
 * Whiteflag authentication token class
 * 
 * <p> This class represents a Whiteflag shared secret authentication token
 * Instances of this class represent the shared secret, and validation data
 * for authentication method 2 can be created.
 * 
 * @wfver v1-draft.6
 * 
 * @wfref 5.1.2.2 Method 2: Shared Token Validation
 * @wfref 5.2.3 Key and Token Derivation
 * 
 * @since 1.1
 */
public class WfAuthToken {

    /* PROPERTIES */
    /**
     * The {@link WfAuthMethod} for which this token is valid
     */
    public final WfAuthMethod method;

    /* The secret authentication token */    
    private final byte[] token;

    /* CONSTRUCTOR */

    /**
     * Constructs a new Whiteflag authentication token
     * @param secret a hexadecimal string with the shared secret used as an authentication token
     */
    public WfAuthToken(String secret) {
        this(WfCryptoUtil.convertToByteArray(secret));
    }

    /**
     * Constructs a new Whiteflag authentication token
     * @param secret a byte array with the shared secret used as an authentication token
     */
    public WfAuthToken(byte[] secret) {
        this.token = secret;
        this.method = TOKEN_PRESHARED;
    }

    /* PUBLIC METHODS */

    /**
     * Generates the Whiteflag verification data to prove possession of the token
     * @param contextInfo a hexadecimal string with information to bind the derived key to the intended context
     * @return a hexadecimal string with the verification data
     */
    public String getVerificationData(String contextInfo) {
        return WfCryptoUtil.convertToHexString(
            getVerificationData(WfCryptoUtil.convertToByteArray(contextInfo))
        );
    }

    /**
     * Generates the Whiteflag verification data to prove possession of the token
     * @param contextInfo a byte array with information to bind the derived key to the intended context
     * @return a byte array with the verification data
     */
    public byte[] getVerificationData(byte[] contextInfo) {
        return WfCryptoUtil.hkdf(token, method.getSalt(), contextInfo, method.getTokenLength());
    }
}
