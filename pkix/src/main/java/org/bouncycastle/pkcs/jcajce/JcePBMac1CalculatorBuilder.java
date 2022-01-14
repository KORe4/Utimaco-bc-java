package org.bouncycastle.pkcs.jcajce;

import java.io.OutputStream;
import java.security.Provider;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PBMAC1Params;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.io.MacOutputStream;
import org.bouncycastle.jcajce.spec.PBKDF2KeySpec;
import org.bouncycastle.jcajce.util.DefaultJcaJceHelper;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.jcajce.util.NamedJcaJceHelper;
import org.bouncycastle.jcajce.util.ProviderJcaJceHelper;
import org.bouncycastle.operator.DefaultMacAlgorithmIdentifierFinder;
import org.bouncycastle.operator.GenericKey;
import org.bouncycastle.operator.MacAlgorithmIdentifierFinder;
import org.bouncycastle.operator.MacCalculator;
import org.bouncycastle.operator.OperatorCreationException;

/**
 * A builder for RFC 8018 PBE based MAC calculators.
 */
public class JcePBMac1CalculatorBuilder
{
    public static final AlgorithmIdentifier PRF_SHA224 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA224, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA256 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA384 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA384, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA512 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA512, DERNull.INSTANCE);

    public static final AlgorithmIdentifier PRF_SHA3_224 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_hmacWithSHA3_224, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA3_256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_hmacWithSHA3_256, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA3_384 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_hmacWithSHA3_384, DERNull.INSTANCE);
    public static final AlgorithmIdentifier PRF_SHA3_512 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_hmacWithSHA3_512, DERNull.INSTANCE);

    private static final DefaultMacAlgorithmIdentifierFinder defaultFinder = new DefaultMacAlgorithmIdentifierFinder();
    
    private JcaJceHelper helper = new DefaultJcaJceHelper();
    private AlgorithmIdentifier macAlgorithm;

    private SecureRandom random;
    private int saltLength = -1;
    private int iterationCount = 4096;
    private int keySize;

    private PBKDF2Params pbeParams = null;
    private AlgorithmIdentifier prf = PRF_SHA256;
    private byte[] salt = null;

    public JcePBMac1CalculatorBuilder(String macAlgorithm, int keySize)
    {
        this(macAlgorithm, keySize, defaultFinder);
    }

    public JcePBMac1CalculatorBuilder(String macAlgorithm, int keySize, MacAlgorithmIdentifierFinder algIdFinder)
    {
        this.macAlgorithm = algIdFinder.find(macAlgorithm);
        this.keySize = keySize;
    }

    public JcePBMac1CalculatorBuilder(PBMAC1Params pbeMacParams)
    {
        this.macAlgorithm = pbeMacParams.getMessageAuthScheme();
        // TODO validate PBE scheme
        this.pbeParams = PBKDF2Params.getInstance(pbeMacParams.getKeyDerivationFunc().getParameters());
    }

    public JcePBMac1CalculatorBuilder setProvider(Provider provider)
    {
        this.helper = new ProviderJcaJceHelper(provider);

        return this;
    }

    public JcePBMac1CalculatorBuilder setProvider(String providerName)
    {
        this.helper = new NamedJcaJceHelper(providerName);

        return this;
    }

    JcePBMac1CalculatorBuilder setHelper(JcaJceHelper helper)
    {
        this.helper = helper;

        return this;
    }

    public JcePBMac1CalculatorBuilder setIterationCount(int iterationCount)
    {
        this.iterationCount = iterationCount;

        return this;
    }

    public JcePBMac1CalculatorBuilder setSaltLength(int saltLength)
    {
        this.saltLength = saltLength;

        return this;
    }

    public JcePBMac1CalculatorBuilder setSalt(byte[] salt)
    {
        this.salt = salt;

        return this;
    }

    public JcePBMac1CalculatorBuilder setRandom(SecureRandom random)
    {
        this.random = random;

        return this;
    }

    public JcePBMac1CalculatorBuilder setPrf(AlgorithmIdentifier prf)
    {
        this.prf = prf;

        return this;
    }

    public MacCalculator build(final char[] password)
        throws OperatorCreationException
    {
        if (random == null)
        {
            random = new SecureRandom();
        }

        try
        {
            final Mac mac = helper.createMac(macAlgorithm.getAlgorithm().getId());

            if (pbeParams == null)
            {
                if (salt == null)
                {
                    if (saltLength < 0)
                    {
                        saltLength = mac.getMacLength();
                    }
                    salt = new byte[saltLength];

                    random.nextBytes(salt);
                }
            }
            else
            {
                salt = pbeParams.getSalt();
                iterationCount = pbeParams.getIterationCount().intValueExact();
                keySize = pbeParams.getKeyLength().intValueExact() * 8;
            }
            
            SecretKeyFactory secFact = helper.createSecretKeyFactory("PBKDF2");

            final SecretKey key = secFact.generateSecret(new PBKDF2KeySpec(password, salt, iterationCount, keySize, prf));

            mac.init(key);

            return new MacCalculator()
            {
                public AlgorithmIdentifier getAlgorithmIdentifier()
                {
                    return new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBMAC1,
                        new PBMAC1Params(
                            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBES2, new PBKDF2Params(salt, iterationCount, (keySize + 7) / 8, prf)),
                            macAlgorithm));
                }

                public OutputStream getOutputStream()
                {
                    return new MacOutputStream(mac);
                }

                public byte[] getMac()
                {
                    return mac.doFinal();
                }

                public GenericKey getKey()
                {
                    return new GenericKey(getAlgorithmIdentifier(), key.getEncoded());
                }
            };
        }
        catch (Exception e)
        {
            throw new OperatorCreationException("unable to create MAC calculator: " + e.getMessage(), e);
        }
    }
}
