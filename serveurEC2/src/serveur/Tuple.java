package serveur;

public class Tuple<T1,T2> {
    public final T1 first;
    public final T2 second;

    public Tuple(T1 t1, T2 t2) {
        first = t1;
        second = t2;
    }
}
