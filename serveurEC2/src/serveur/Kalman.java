package serveur;

import java.util.ArrayList;
import org.ejml.simple.SimpleMatrix;
import java.lang.Math;


public class Kalman {

    public ArrayList<Tuple<Double,Double>> coordinates;
    public ArrayList<Tuple<Double,Double>> vitesses;
    public ArrayList<Tuple<Double,Double>> oldPos; // Ancienne position
    public ArrayList<SimpleMatrix> covars; // Matrice de covariance propre à chaque appareil
    private ArrayList<SimpleMatrix> As; // Matrice de la dynamique propre à chaque appareil
    private static double deltaT; // le temps entre 2 messages ce serait vraiment bien que ce soit constant
    private static final double Sigma1 = 1;
    private static final double Sigma2 = 1;
    private SimpleMatrix A = SimpleMatrix.identity(2); //Matrice de la dynamique qui va varier en fonction du temps pour chaque utilisateur. Au début des 0
    private SimpleMatrix B = SimpleMatrix.identity(2); //Matrice de la mesure
    private SimpleMatrix Q = new SimpleMatrix(2,2,true,new double[]{Sigma1,0,0,Sigma1}); //Covariance de la prédiction
    private SimpleMatrix R = new SimpleMatrix(2,2,true,new double[]{Sigma2,0,0,Sigma2});; //Covariance de la mesure

    public Kalman () {
        coordinates = new ArrayList<Tuple<Double,Double>>();
        vitesses = new ArrayList<Tuple<Double,Double>>();
        oldPos = new ArrayList<Tuple<Double,Double>>();
        covars = new ArrayList<SimpleMatrix>();
    }
    
    public static Tuple<Double,Double> update_vitesse(Tuple<Double,Double> pos1, Tuple<Double,Double> pos2) {
    	return new Tuple<Double,Double>((pos2.first - pos1.first)/deltaT, (pos2.second - pos2.second)/deltaT);
    }
    
    //à utiliser à chaque nouvelle mesure, met à jour la dynamique et le filtre de kalman
    public void update_Kalman(int id, Tuple<Double,Double> newPos) {
    	//Update vitesse
    	vitesses.set(id, update_vitesse(oldPos.get(id),newPos));
    	SimpleMatrix P = covars.get(id);
    	SimpleMatrix K = P.mult(B.transpose());
    	SimpleMatrix K_bis = B.mult(P).mult(B.transpose()).plus(R);
    	K = K.mult(K_bis.invert());
    	SimpleMatrix coor = new SimpleMatrix(2,1,true,new double[]{coordinates.get(id).first, coordinates.get(id).second});
    	SimpleMatrix new_coor = new SimpleMatrix(2,1,true,new double[]{newPos.first, newPos.second});
    	coor = coor.plus(K.mult(new_coor.minus(B.mult(coor))));
    	coordinates.set(id, new Tuple<Double,Double>(coor.get(0),coor.get(1)));
    	covars.set(id, SimpleMatrix.identity(2).minus(K.mult(B)).mult(P));
    }
    
    //Met à jour la position avec celle prévue, et met à jour la matrice de covariance de l'indice id
    public void prev_Kalman(int id) {
    	SimpleMatrix coor = new SimpleMatrix(2,1,true,new double[]{coordinates.get(id).first, coordinates.get(id).second});
    	SimpleMatrix prev = new SimpleMatrix(2,1,true,new double[]{coordinates.get(id).first + deltaT*vitesses.get(id).first, coordinates.get(id).second + deltaT*vitesses.get(id).second});
    	oldPos.set(id, coordinates.get(id));
    	coordinates.set(id, new Tuple<Double,Double>(prev.get(0),prev.get(1)));
    	covars.set(id, A.mult(covars.get(id).mult(A.transpose())).plus(Q));
    }

}
