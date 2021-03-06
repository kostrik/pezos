package utils;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import ove.crypto.digest.Blake2b;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.*;

public class Utils {
	/////////////// SEND TO SOCKET 

	public static void sendBytesToSocket(byte[] bytesArrayToSend, DataOutputStream out) throws IOException, DecoderException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(to2BytesArray(bytesArrayToSend.length));
		outputStream.write(bytesArrayToSend);
		bytesArrayToSend = outputStream.toByteArray(); 
		out.write(bytesArrayToSend); 
		out.flush(); // the last of the data gets out to the file
		//System.out.println("*** SOCKET sent     : "+toHexString(bytesArrayToSend));
	}

	public static void sendInjectOperation(byte[] content, String pk, String sk, DataOutputStream out) throws DataLengthException, org.apache.commons.codec.DecoderException, CryptoException, IOException{
		byte[] pkBytes   = toBytesArray(pk);
		byte[] signature = signature(hash(concatArrays(content, pkBytes),32), sk);
		byte[] message   = concatArrays(to2BytesArray(9),content,pkBytes,signature);
		sendBytesToSocket(message, out);
        System.out.println("  ERROR -> INJECT OPERATION "+Utils.toHexString(Arrays.copyOfRange((message),0,2))+"."+Utils.toHexString(Arrays.copyOfRange((message),2,4))+"."+Utils.toHexString(Arrays.copyOfRange((message),4,message.length)));
	}

	public static void sendPkToSocket(DataOutputStream out,String pk) throws IOException, DecoderException {
		sendBytesToSocket(toBytesArray(pk),out);
	}

	public static void sendSignatureToSocket(DataOutputStream out, byte[] seed, String sk) throws DataLengthException, DecoderException, CryptoException, IOException {
		byte[] hashSeed = hash(seed,32);
		byte[] signature = signature(hashSeed,sk);
		Utils.sendBytesToSocket(signature,out);
	}

	/////////////// GET FROM TO SOCKET 

	public static byte[] getBytesFromSocket(int nbBytesWanted, DataInputStream in) throws IOException {
		byte[] receivedMessage = new byte[nbBytesWanted];
		in.read(receivedMessage,0,nbBytesWanted); // if no wifi, blocked for 15 minutes then IOExcetion // blocks until data available / EOF / exception 
		//System.out.println("*** SOCKET received : "+toHexString(receivedMessage));
		return receivedMessage;
	}
	
	public static byte[] getNextObjectFromSocket(int expectedTag, DataInputStream in) throws IOException, BroadcastInsteadOfAnswerException {
		int tag = toInt(getBytesFromSocket(2,in));

		byte[] receivedMessage = null;

		if(tag==2 || tag ==4) {
			receivedMessage = concatArrays(to2BytesArray(tag),getBytesFromSocket(172,in));
		}
		else if(tag==6) {
	        byte[] nbBytesToRead = getBytesFromSocket(2,in);
			byte[] receivedOperations = getBytesFromSocket(toInt(nbBytesToRead),in);
			receivedMessage = concatArrays(to2BytesArray(tag),nbBytesToRead,receivedOperations);			
		}
		else if(tag==8) { 
			byte[] dictatorPk           = getBytesFromSocket(32,in);
			byte[] predTimestamp        = getBytesFromSocket(8,in);
			byte[] nbBytesNextSequence  = getBytesFromSocket(4,in);
	        byte[] accounts             = getBytesFromSocket(toInt(nbBytesNextSequence),in);
	        receivedMessage = concatArrays(to2BytesArray(tag),dictatorPk,predTimestamp,nbBytesNextSequence,accounts);
		}
		else {
			throw new IOException("Not expected tag "+tag + " instead of "+expectedTag);
		}

		if(tag!=2 && tag!=expectedTag)
			throw new IOException("Not expected tag "+tag + " instead of "+expectedTag);

		if(tag==2 && tag!=expectedTag)
			throw new BroadcastInsteadOfAnswerException("I have got broadcast instead of tag "+tag,receivedMessage);
		
		// System.out.println(whatIHaveReceived(receivedMessage, nbBytesReceived));
		return receivedMessage;
	}	
	
	public static byte[] getBlockOfLevel(int level, DataOutputStream out, DataInputStream  in) throws org.apache.commons.codec.DecoderException, IOException, BroadcastInsteadOfAnswerException {
        sendBytesToSocket(concatArrays(to2BytesArray(3),to4BytesArray(level)),out);
        return getNextObjectFromSocket(4,in);
	}
	
	public static byte[] getCurrentHead(DataOutputStream out, DataInputStream  in) throws IOException, org.apache.commons.codec.DecoderException {
		// ignore tags 4,6,8
		sendBytesToSocket(to2BytesArray(1),out);
		return getBroadcast(in);
	}
	
	public static byte[] getBroadcast(DataInputStream  in) throws IOException, org.apache.commons.codec.DecoderException {
		// ignore tags 4,6,8
        byte[] receivedMessage = null;
        while(true) {
        	try {
        	receivedMessage = getNextObjectFromSocket(2,in);
        	} catch (BroadcastInsteadOfAnswerException e) {
        		continue;
        	}
        	break;
        }
        return receivedMessage;
	}

	public static byte[] getListOperations(int level, DataOutputStream out, DataInputStream  in) throws org.apache.commons.codec.DecoderException, IOException, BroadcastInsteadOfAnswerException {
        sendBytesToSocket(concatArrays(to2BytesArray(5),to4BytesArray(level)),out);
        byte[] result = getNextObjectFromSocket(6,in);
        return result;
	}
	
	public static byte[] getState(int level, DataOutputStream out, DataInputStream  in) throws org.apache.commons.codec.DecoderException, IOException, BroadcastInsteadOfAnswerException {
        sendBytesToSocket(concatArrays(to2BytesArray(7),to4BytesArray(level)),out);
        return getNextObjectFromSocket(8,in);
	}

	public static byte[] getSeed(DataInputStream  in) throws IOException {
		byte[] receivedMessage = new byte[24];
		in.read(receivedMessage,0,24);
		return receivedMessage;	
	}

	/////// CRYPTO
	
	public static byte[] hash(byte[] valeurToHash, int hashParamNbBytes) {
		Blake2b.Param param = new Blake2b.Param().setDigestLength(hashParamNbBytes);
		final Blake2b blake2b = Blake2b.Digest.newInstance(param);        
		return blake2b.digest(valeurToHash);
	}
	
	public static byte[] signature(byte[] msgToSign, String sk) throws DecoderException, DataLengthException, CryptoException {
		Ed25519PrivateKeyParameters sk2 = new Ed25519PrivateKeyParameters(toBytesArray(sk));
		Signer signer = new Ed25519Signer();
		signer.init(true, sk2);
		signer.update(msgToSign, 0, 32);
		return signer.generateSignature();
	}
	
	public static boolean signatureIsCorrect(byte[] signedData, byte[] signatureToVerify, byte[] pkAsBytes, DataOutputStream out, DataInputStream in) throws InvalidKeyException, SignatureException, InvalidKeySpecException, NoSuchAlgorithmException, IOException, org.apache.commons.codec.DecoderException{
		// Edwards-curve Digital Signature Algorithm (EdDSA) 
		// bonnes performances + en ??vitant les probl??mes de s??curit?? qui sont apparus dans les autres sch??mas 
		// r??sistance aux attaques comparable ?? celle des chiffrements de 128-bits de qualit??
		// ???????????????????? ?????????????????????????????????????? ?????????????????? ?????????????????????? ????????????????????????????????
		// EdDSA calcule ce nonce unique pour chaque signature =hachage(sk, data), plut??t que de d??pendre d'un g??n??rateur de nombre al??atoire => r??duit les risques d'une attaque sur le g??n??rateur de nombres al??atoires, sans l'??liminer compl??tement lorsque des nombres al??atoires sont utilis??s pour la g??n??ration des cl??s
		SubjectPublicKeyInfo pkInfo               = new SubjectPublicKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),pkAsBytes);
		X509EncodedKeySpec   keySpec              = new X509EncodedKeySpec(pkInfo.getEncoded());
		BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();
		Signature            signatureToVerify2   = Signature.getInstance("Ed25519",bouncyCastleProvider);
		PublicKey            pkAsPublicKey        = KeyFactory.getInstance("Ed25519",bouncyCastleProvider).generatePublic(keySpec);
		signatureToVerify2.initVerify(pkAsPublicKey);
		signatureToVerify2.update(signedData);
		return signatureToVerify2.verify(signatureToVerify);
	 }
	
	///////////// CONVERTERS

	public static String toHexString(byte[] bytes) {
		if(bytes==null || bytes.length==0) return "";
		final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	    byte[] hexChars = new byte[bytes.length * 2];
	    for (int j = 0; j < bytes.length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars, StandardCharsets.UTF_8);
	}
	
	public static long toDateAsSeconds(String dateAsString) throws ParseException { 
		DateTimeFormatter formatter     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC")); 
		LocalDateTime     localDateTime = LocalDateTime.parse(dateAsString, formatter);
		return localDateTime.atZone(ZoneId.of("UTC")).toEpochSecond(); 
	}
	
	public static long currentDateTimeAsSeconds() {
		return LocalDateTime.now(ZoneId.of("UTC")).atZone(ZoneId.of("UTC")).toEpochSecond(); 
	}
	
	public static int toInt(byte[] bytes) {
		if(bytes.length==4)
			return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
		if(bytes.length==2)
			return (int)ByteBuffer.wrap(bytes).getShort();
		return 0;
	}

	public static long toLong(byte[] bytes) {
	    return ByteBuffer.wrap(bytes).getLong();
	}
	
	public static byte[] to2BytesArray(int n) { 
		ByteBuffer convertedToBytes = ByteBuffer.allocate(2);
		convertedToBytes.putShort((short)n);
		return convertedToBytes.array();
	}
	
	public static byte[] to4BytesArray(int n) {
		ByteBuffer convertedToBytes = ByteBuffer.allocate(4);
		convertedToBytes.putInt(n);
		return convertedToBytes.array();
	}
	
	public static byte[] to8BytesArray(long n) { 
		ByteBuffer convertedToBytes = ByteBuffer.allocate(8);
		convertedToBytes.putLong(n);
		return convertedToBytes.array();
	}
	
	public static byte[] to32bytesArray(int n) { 
		ByteBuffer convertedToBytes = ByteBuffer.allocate(32);
		convertedToBytes.putInt(n);
		return convertedToBytes.array();
	}
	
	public static String toHexString(int n) {
		return toHexString(to4BytesArray(n));
	}
	
	public static byte[] toBytesArray(char[] charArray) throws DecoderException {
		return Hex.decodeHex(charArray);
	}
	
	public static byte[] toBytesArray(String str) throws DecoderException {
		return Hex.decodeHex(str.toCharArray());
	}
	
	public static String toDateAsString(long seconds) { 
		LocalDateTime     dateTime      = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
		DateTimeFormatter formatter     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String            formattedDate = dateTime.format(formatter);
		return formattedDate.toString();
	}

	public static String toDateAsString(byte[] dateAsBytes) { 
		LocalDateTime     dateTime      = LocalDateTime.ofEpochSecond(toLong(dateAsBytes), 0, ZoneOffset.UTC);
		DateTimeFormatter formatter     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String            formattedDate = dateTime.format(formatter);
		return formattedDate.toString();
	}

	public static byte[] concatArrays(byte[] a, byte[] b) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(a);
		outputStream.write(b);
		return outputStream.toByteArray(); 
	}

	public static byte[] concatArrays(byte[] a, byte[] b, byte[] c) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(a);
		outputStream.write(b);
		outputStream.write(c);
		return outputStream.toByteArray(); 
	}

	public static byte[] concatArrays(byte[] a, byte[] b, byte[] c, byte[] d) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(a);
		outputStream.write(b);
		outputStream.write(c);
		outputStream.write(d);
		return outputStream.toByteArray(); 
	}

	public static byte[] concatArrays(byte[] a, byte[] b, byte[] c, byte[] d, byte[] e) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(a);
		outputStream.write(b);
		outputStream.write(c);
		outputStream.write(d);
		outputStream.write(e);
		return outputStream.toByteArray(); 
	}		
}