package serveur;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttManager {
	private IMqttClient publisher;
	private String caFilePath;
	private String clientCrtFilePath;
	private String clientKeyFilePath;
	
	public MqttManager(String publisherId, String broker, String caFilePath, String clientCrtFilePath, String clientKeyFilePath) {
		this.caFilePath = caFilePath;
		this.clientCrtFilePath = clientCrtFilePath;
		this.clientKeyFilePath = clientKeyFilePath;
		try {
			this.publisher = new MqttClient(broker, publisherId);			
		} catch (MqttException e) {
			System.err.println(e.getMessage());
		}
	}
	
	
	/*
	 * Etablissement d'une connexion MQTT sur le serveur AWS IoT distant
	 */
	public void etablissementConnexion() {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);
		options.setCleanSession(true);
		options.setConnectionTimeout(10);

		try {
			SSLSocketFactory socketFactory = getSocketFactory(this.caFilePath, this.clientCrtFilePath, this.clientKeyFilePath, "");
			options.setSocketFactory(socketFactory);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		System.out.println("Connexion au serveur IoT...");
		try {			
			publisher.connect(options);
		} catch (MqttException e) {
			System.err.println(e.getMessage());
		}
		System.out.println("Connexion réussie !");
	}
	
	
	/*
	 * Publication d'un message sur un topic
	 */
	public void publish(String topic, String msg) {
		if (!publisher.isConnected()) {
			System.out.println("ERREUR CONNEXION");
		}
		MqttMessage mqttMsg = new MqttMessage((msg).getBytes());
		mqttMsg.setQos(0);
		mqttMsg.setRetained(true);
		try {
			publisher.publish(topic, mqttMsg);
		} catch (MqttException e) {
			System.err.println(e.getMessage());
		}
	}
	
	private SSLSocketFactory getSocketFactory(final String caCrtFile, final String crtFile, final String keyFile,
			final String password) throws Exception {
		Security.addProvider(new BouncyCastleProvider());

		// load CA certificate
		X509Certificate caCert = null;

		FileInputStream fis = new FileInputStream(caCrtFile);
		BufferedInputStream bis = new BufferedInputStream(fis);
		CertificateFactory cf = CertificateFactory.getInstance("X.509");

		while (bis.available() > 0) {
			caCert = (X509Certificate) cf.generateCertificate(bis);
		}

		// load client certificate
		bis = new BufferedInputStream(new FileInputStream(crtFile));
		X509Certificate cert = null;
		while (bis.available() > 0) {
			cert = (X509Certificate) cf.generateCertificate(bis);
		}

		// load client private key
		PEMParser pemParser = new PEMParser(new FileReader(keyFile));
		Object object = pemParser.readObject();
		PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
		JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
		KeyPair key;
		if (object instanceof PEMEncryptedKeyPair) {
			System.out.println("Encrypted key - we will use provided password");
			key = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
		} else {
			System.out.println("Unencrypted key - no password needed");
			key = converter.getKeyPair((PEMKeyPair) object);
		}
		pemParser.close();

		// CA certificate is used to authenticate server
		KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
		caKs.load(null, null);
		caKs.setCertificateEntry("ca-certificate", caCert);
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
		tmf.init(caKs);

		// client key and certificates are sent to server so it can authenticate
		// us
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);
		ks.setCertificateEntry("certificate", cert);
		ks.setKeyEntry("private-key", key.getPrivate(), password.toCharArray(),
				new java.security.cert.Certificate[] { cert });
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, password.toCharArray());

		// finally, create SSL socket factory
		SSLContext context = SSLContext.getInstance("TLSv1.2");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		return context.getSocketFactory();
	}
}
