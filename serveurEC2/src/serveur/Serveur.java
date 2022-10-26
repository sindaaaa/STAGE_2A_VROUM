package serveur;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

public class Serveur {

	private static final int NB_MAX_ROBOTS = 10;
	private static final int NB_MAX_VULNERABLES = 10;


	private int nbRobots;
	private int nbVulnerable;
	
	private HashMap<Integer, CoordonneesGps> coordonneesVulnerables;
	private HashMap<Integer, CoordonneesGps> coordonneesRobots;

	private ArrayList<String> adressesRobots;
	private ArrayList<String> adressesVulnerables;

	private ArrayList<Socket> socketsRobots;
	private ArrayList<Socket> socketsVulnerables;

	private ServerSocket serverSocket;

	public Serveur() {
		this.coordonneesVulnerables = new HashMap<Integer, CoordonneesGps>();
		this.coordonneesRobots = new HashMap<Integer, CoordonneesGps>();
		
		this.adressesVulnerables = new ArrayList<String>();
		this.adressesRobots = new ArrayList<String>();
		
		this.nbVulnerable = 0;
		this.nbRobots = 0;
		
		this.socketsRobots = new ArrayList<Socket>();
		this.socketsVulnerables = new ArrayList<Socket>();
		try {
			this.serverSocket = new ServerSocket(8888);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private static class UpdateCoordinates extends Thread {

		private Serveur serv;
		private Socket socket;
		private String adresseIP;
		private MqttManager mqttManager;
		private int id;
		private boolean vulnerable;

		public UpdateCoordinates(Serveur server, Socket s, String adresseIP, MqttManager mqttManager, boolean vulnerable) {
			this.serv = server;
			this.socket = s;
			this.vulnerable = vulnerable;
			this.mqttManager = mqttManager;
			this.adresseIP = adresseIP;
			
			//attribution d'un id libre
			boolean idAttribue = false;
			int i = 0;
			if (vulnerable) {
				while (i < NB_MAX_VULNERABLES && !idAttribue) {
					if (!server.coordonneesVulnerables.containsKey(i)) {					
						this.id = i;
						idAttribue = true;
					}
					i++;
				}				
			} else {
				while (i < NB_MAX_ROBOTS && !idAttribue) {
					if (!server.coordonneesRobots.containsKey(i)) {					
						this.id = i;
						idAttribue = true;
					}
					i++;
				}	
			}
		}

		@Override
		public void run() {
			Gson gson = new Gson();
			boolean cond = true;
			
			try {
				InputStreamReader isr = new InputStreamReader(socket.getInputStream());
				BufferedReader br = new BufferedReader(isr);
				String coordString;
				
				while (cond) {
					coordString = br.readLine();
					
					if (coordString != null) {
						String[] coords = coordString.split(";");
						
						//Mise a jour des coordonnées
						Double lat = Double.parseDouble(coords[1]);
						Double lon = Double.parseDouble(coords[2]);
						CoordonneesGps coord = new CoordonneesGps(lat, lon);
						if (vulnerable) {
							serv.coordonneesVulnerables.put(this.id, coord);							
							System.out.println("Coordonnees vulnerable " + this.id + " : " + lat + ";" + lon);
							this.mqttManager.publish("vulnerable/" + Integer.toString(this.id) + "/gps", gson.toJson(coord));
						} else {
							serv.coordonneesRobots.put(this.id, coord);							
							System.out.println("Coordonnees robot " + this.id + " : " + lat + ";" + lon);
							this.mqttManager.publish("robot/" + Integer.toString(this.id) + "/gps", gson.toJson(coord));
						}
					} else {
						cond = false;
					}
				}

				//Fermeture socket et suppression des adresses
				isr.close();
				socket.close();
				if (vulnerable) {					
					serv.coordonneesVulnerables.remove(this.id);
					serv.adressesVulnerables.remove(this.adresseIP);
					serv.socketsVulnerables.remove(this.socket);
					serv.nbVulnerable --;
				} else {
					serv.coordonneesRobots.remove(this.id);
					serv.adressesRobots.remove(this.adresseIP);
					serv.socketsRobots.remove(this.socket);
					serv.nbRobots --;
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}


	private static class Calculations extends Thread {

		private Serveur serv;
		private Kalman kalman;
		private MqttManager mqttManager;

		public Calculations(Serveur server, Kalman kalman, MqttManager mqttManager) {
			this.serv = server;
			this.mqttManager = mqttManager;
			this.kalman = kalman;
		}

		@Override
		public void run() {
			Gson gson = new Gson();
			
			while (true) {
				// TODO: PREDICTION DE TRAJECTOIRE KALMAN
				// Update of the dynamic and forecasting
//				for (int i = 0; i < serv.coordinates.size(); i++) {
//					kalman.update_Kalman(i, serv.coordinates.get(i));
//					kalman.prev_Kalman(i);
//				}

				// Check potential collisions
				if (serv.coordonneesRobots.size() > 0 && serv.coordonneesVulnerables.size() > 0) {
					JSONObject colJson = Serveur.anyCollisions(serv.coordonneesVulnerables, serv.coordonneesRobots);
					String vulnerables = colJson.get("vulnerablesCollisions").toString();
					String collisions = colJson.get("robotsCollisions").toString();
					
					//envoi des collisions sur AWS IoT
					colJson.remove("vulnerablesCollisions"); //pas besoin de la liste des vulnerables qui sont
															 //en collisions avec des robots
					mqttManager.publish("collisions", colJson.toString());
					
					// Envoie d'une alerte s'il y a au moins une collision
					if (!colJson.isEmpty()) {
						serv.sendAlert(collisions, vulnerables);
					}
				}
				try {
					Thread.sleep(2000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	
	public static void main(String[] args) throws Exception {

		Serveur serv = new Serveur();
		Kalman kalman = new Kalman();
		boolean premiereConnexion = true;
		
		// mise en place de la connexion MQTT
		String broker = "ssl://a29x7dzqjsbfch-ats.iot.us-east-1.amazonaws.com:8883";
		String caFilePath = "/home/ubuntu/serveurGPS/certificates/AmazonRootCA1.pem";
		String clientCrtFilePath = "/home/ubuntu/serveurGPS/certificates/95aceecb9305c301b68d46aa6b16865db0ac90cb5fcf87a4253542d15040bcb0-certificate.pem.crt";
		String clientKeyFilePath = "/home/ubuntu/serveurGPS/certificates/95aceecb9305c301b68d46aa6b16865db0ac90cb5fcf87a4253542d15040bcb0-private.pem.key";
		String publisherId = UUID.randomUUID().toString();
		
		MqttManager mqttManager = new MqttManager(publisherId, broker, caFilePath, clientCrtFilePath, clientKeyFilePath);
		mqttManager.etablissementConnexion();

		//Attente de nouvelles connexions
		while (true) {
			
			System.out.println("Attente connexion d'un appareil...");
			Socket socket = serv.serverSocket.accept();
			System.out.println("Nouvel appareil connecté !");

			InputStreamReader isr = new InputStreamReader(socket.getInputStream());
			BufferedReader br = new BufferedReader(isr);

			ajoutAppareil(serv, mqttManager, socket, br);

			// Lance les calculs de collision lors de la premiere connexion
			if (premiereConnexion) {
				Thread calc = new Calculations(serv, kalman, mqttManager);
				calc.start();
				premiereConnexion = false;
			}
		}
	}


	private static void ajoutAppareil(Serveur serv, MqttManager mqttManager, Socket socket, BufferedReader br)
			throws IOException {
		// Identification du type de l'appereil (vulnerable ou robot)
		String type = br.readLine();
		
		// Reception de l'adresse utilisateur
		String adresseIP = br.readLine();

		// Verification de la presence de l'appareil dans nos donnees
		int id;
		if (type.equals("vulnerable")) {
			id = serv.adressesVulnerables.indexOf(adresseIP);
			if (id == -1) {
				serv.nbVulnerable ++;
				serv.adressesVulnerables.add(adresseIP);
				serv.socketsVulnerables.add(socket);
				Thread t = new UpdateCoordinates(serv, socket, adresseIP, mqttManager, true);
				t.start();
			}				
		} else {
			id = serv.adressesRobots.indexOf(adresseIP);
			if (id == -1) {					
				serv.nbRobots ++;
				serv.adressesRobots.add(adresseIP);
				serv.socketsRobots.add(socket);
				Thread t = new UpdateCoordinates(serv, socket, adresseIP, mqttManager, false);
				t.start();
			}
		}
	}
	
	
	public static JSONObject anyCollisions(Map<Integer, CoordonneesGps> coordVul, Map<Integer, CoordonneesGps> coordRobots) {
		ArrayList<Tuple<Integer, Integer>> collisions = new ArrayList<Tuple<Integer, Integer>>();
		ArrayList<Integer> robotsCollisions = new ArrayList<Integer>();
		ArrayList<Integer> vulnerablesCollisions = new ArrayList<Integer>();
		Gson gson = new Gson();
		double distanceCollision = 7.0;
		
		for (int i = 0; i < coordRobots.size() - 1; i++) {
			double lat1 = Math.toRadians(coordRobots.get(i).getLatitude());
			double lon1 = Math.toRadians(coordRobots.get(i).getLongitude());
			
			//verification collision avec les vulnerables
			for (int j = 0; j < coordVul.size(); j++) {
				double lat2 = Math.toRadians(coordVul.get(j).getLatitude());
				double lon2 = Math.toRadians(coordVul.get(j).getLongitude());
				double d = calculDistance(lat1, lon1, lat2, lon2);
				if (d <= distanceCollision) {
					collisions.add(new Tuple<Integer, Integer>(i, j));
					if (!robotsCollisions.contains(i)) {
						robotsCollisions.add(i);
					}
					if (!vulnerablesCollisions.contains(j)) {
						vulnerablesCollisions.add(j);
					}
				}
			}
			
			//verification collision avec les autres robots
			for (int j = i + 1; j < coordRobots.size(); j++) {
				double lat2 = Math.toRadians(coordRobots.get(j).getLatitude());
				double lon2 = Math.toRadians(coordRobots.get(j).getLongitude());
				double d = calculDistance(lat1, lon1, lat2, lon2);
				if (d <= distanceCollision) {
					collisions.add(new Tuple<Integer, Integer>(i, j));
					robotsCollisions.add(j);
					if (!robotsCollisions.contains(i)) {
						robotsCollisions.add(i);
					}
				}
			}
		}
		
		//conversion de l'objet en json
		JSONParser parser = new JSONParser();
		JSONArray colJson = new JSONArray();
		JSONObject json = new JSONObject();
		try {
			colJson = (JSONArray) parser.parse(gson.toJson(collisions));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		json.put("collisions", colJson);
		json.put("robotsCollisions", robotsCollisions);
		json.put("vulnerablesCollisions", vulnerablesCollisions);
		
		return json;
	}


	private static double calculDistance(double lat1, double lon1, double lat2, double lon2) {
		double d = 2 * 6371000 * Math.asin(Math.sqrt(Math.pow(Math.sin((lat2 - lat1)/2), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon2 - lon1)/2), 2)));
		System.out.println("Distance : " + d);
		return d;
	}
	
	private void sendAlert(String collisions, String vullnerables) {
		collisions = collisions.substring(1, collisions.length() - 1);
		vullnerables = vullnerables.substring(1, vullnerables.length() - 1);
		
		
		String[] col = collisions.split(", ");
		String[] vul = vullnerables.split(", ");
		
		String message = "Alert\n";
		sendAlertFromList(col, message, false);
		sendAlertFromList(vul, message, true);
	}


	private void sendAlertFromList(String[] listId, String message, boolean vulnerable) {
		ArrayList<Socket> listSocket;
		if (vulnerable) {
			listSocket = this.socketsVulnerables;			
		} else {
			listSocket = this.socketsRobots;
		}
		
		if (!listId[0].equals("")) {			
			for (int i = 0; i < listId.length; i++) {
				Integer id = Integer.decode(listId[i]);
				try {
					Socket s = listSocket.get(id);
					PrintWriter pw = new PrintWriter(s.getOutputStream());
					pw.write(message);
					pw.flush();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
