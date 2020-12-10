package CSC583;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Watson {
    // Files
    public static final String questionsFilePath = "questions.txt";
    public static final String wikipediaFilesPath = "src/main/resources/wiki-subset/";
    public static final String luceneOutputDir = "src/main/resources/lucene-files/";


    public static void main(String[] args) {


        System.out.println("-----------------------------Watson-----------------------------");
        System.out.println("----------------------------Jeopardy----------------------------");

        boolean verbose = parseArgs(args, "-v");
        boolean parse = parseArgs(args, "-p");
        boolean queryGiven = parseArgs(args, "-q");
        String query = parseArgs(args);

        if (queryGiven) {
            verbose = true;
        }

        IndexReader wikipediaIndex;

        Parser parser = new Parser();
        if (parse) {
            parser.parse();
            wikipediaIndex = parser.getIndex();

        } else {
            wikipediaIndex = loadLuceneIndex();
            parser.setLuceneIndex(wikipediaIndex);
        }

        // Write queries in
        ArrayList<String> queries = new ArrayList<String>();
        if (queryGiven) {
            queries.add(query);
        } else {
            //Get queries from file
            queries.addAll(getQueriesFromFile());
        }

        //parse, lemmenize, and tokenize query(/ies)
        HashMap<String, String> lemmenizedQueries = Lemmenizer.lemmenizeQueries(queries);

        //run query, score documents
        HashMap<String, ArrayList<ScoreDoc>> scores = parser.score(lemmenizedQueries);

        // Give verbose output if requested
        if (verbose) {
            // print out top 10 scores for each query with orignial query and top 10
            for (String answer : queries) {
                System.out.println("Query: " + answer);
                String lemmenized = lemmenizedQueries.get(answer);
                System.out.println("Tokenized query: " + lemmenized);
                for (ScoreDoc document : scores.get(lemmenized)) {
                    System.out.println("Answer: " + document.getDocumentID() + "Score " + document.getScore());
                }
            }
        }

        HashMap<String, ArrayList<String>> questionKey = new HashMap<String, ArrayList<String>>();
        if (!queryGiven) {
            //load in question key
            questionKey.putAll(loadInQuestionKey());
        }

        int questionsCorrect = 0;
        int i = 1;

        // Print results
        for (String answer : queries) {
            // Print answer to the queries
            System.out.println("The clue is:" + answer);
            String question = scores.get(lemmenizedQueries.get(answer)).get(0).getDocumentID();

            // Show correct or wrong.
            if (!queryGiven) {
                // If the top hit is the same as the key, it is correct
                if (isCorrectQuestion(question, questionKey.get(answer))) {
                    System.out.println("CORRECT! \n");
                    questionsCorrect++;
                } else {
                    String[] s = question.split(" ");
                    for (String temp : s) {
                        if (answer.contains(temp)) {
                            String nextQuestion = scores.get(lemmenizedQueries.get(answer)).get(1).getDocumentID();
                            for (String temp1 : nextQuestion.split(" ")) {
                                if (answer.contains(temp1)) {
                                    question = scores.get(lemmenizedQueries.get(answer)).get(2).getDocumentID();
                                }
                            }
                        }
                    }
                }
                System.out.println("What is " + question + "?");

                for (String response : questionKey.get(answer)) {
                    System.out.println("WRONG!" + "The correct answer is " + response + "? \n");
                }

            }
        }

        if (!queryGiven) {
            double score = ((double) questionsCorrect) / 100 * 100;
            System.out.println(questionsCorrect + " out of 100 are correct.");
            System.out.printf("Accuracy is %.02f %%.\n", score);
        }

        System.out.println("-----------------------------Watson-----------------------------");
        System.out.println("----------------------------Jeopardy----------------------------");

    }


    public static String parseArgs(String[] args) {
        //If -q is present, then all following arguments are the query
        String query = "";
        boolean startQuery = false;
        if (parseArgs(args, "-q")) {
            for (String arg : args) {
                if (startQuery)
                    query += " " + arg;
                if (arg.indexOf("-q") != -1)
                    startQuery = true;
            }
        }
        return query.trim();
    }

    public static boolean parseArgs(String[] args, String param) {
        //-v, -p, -q
        for (String arg : args) {
            if (arg.indexOf(param) == 0)
                return true;
        }
        return false;
    }

    public static ArrayList<String> getQueriesFromFile() {
        ArrayList<String> queries = new ArrayList<String>();
        for (String query : loadInQuestionKey().keySet()) {
            queries.add(query);
        }
        return queries;
    }

    //ArrayList<String> is the list of all possible responses to the clues.
    public static HashMap<String, ArrayList<String>> loadInQuestionKey() {
        HashMap<String, ArrayList<String>> questionKey = new HashMap<String, ArrayList<String>>();

        boolean USING_CATEGORIES = true;

        try {
            ClassLoader classLoader = Watson.class.getClassLoader();
            File file = new File(classLoader.getResource(questionsFilePath).getFile());
            Scanner scanner = new Scanner(file);

            int lineCount = 0;
            String category = "";
            String query = "";
            while (scanner.hasNextLine()) {
                lineCount++;
                if (lineCount == 1) {
                    //Category
                    if (USING_CATEGORIES) {
                        category = " " + scanner.nextLine().trim();
                    } else {
                        scanner.nextLine(); //skip this line
                    }
                } else if (lineCount == 2) {
                    //Query
                    query = scanner.nextLine().trim() + category;
                } else if (lineCount == 3) {
                    //Potential correct Questions
                    ArrayList<String> questions = new ArrayList<String>();
                    for (String goodQuestion : scanner.nextLine().trim().split("\\|")) {
                        questions.add(goodQuestion.trim());
                    }
                    questionKey.put(query, questions);
                } else {
                    //Verify that the line is blank.
                    String blankLine = scanner.nextLine().trim();
                    if (!blankLine.isEmpty()) {
                        System.err.println("Error.");
                    }
                    lineCount = 0;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return questionKey;
    }

    public static IndexReader loadLuceneIndex() {
        try {
            Directory index = FSDirectory.open(Paths.get(luceneOutputDir));
            IndexReader reader = DirectoryReader.open(index);
            return reader;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isCorrectQuestion(String question, ArrayList<String> potentialQuestions) {
        for (String potentialQuestion : potentialQuestions) {
            if (potentialQuestion.equalsIgnoreCase(question)) {
                return true;
            }
        }
        return false;
    }

}
