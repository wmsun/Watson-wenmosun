package CSC583;

public class ScoreDoc implements Comparable<ScoreDoc> {

    String documentID;
    double score;

    public ScoreDoc(String documentID, double score) {
        this.documentID = documentID.substring(2, documentID.length() - 2); //Remove [[ ]]
        this.score = score;
    }

    public String getDocumentID() {
        return this.documentID;
    }

    public double getScore() {
        return this.score;
    }

    @Override
    public int compareTo(ScoreDoc o) {

        return ((Double) o.score).compareTo(score);
    }

}
