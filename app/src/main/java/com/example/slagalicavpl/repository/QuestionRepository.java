package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionRepository {

    private static QuestionRepository instance;

    private static final Question[] ALL = {
        new Question("Koji grad je prestonica Francuske?",
                "Barselona", "Pariz", "Brisel", "Rim", 'B'),
        new Question("Koja je najduža reka u Evropi po obimu vode?",
                "Rajna", "Dunav", "Volga", "Temza", 'C'),
        new Question("Koji je najviši vrh Alpa?",
                "Matterhorn", "Grossglockner", "Jungfrau", "Mont Blanc", 'D'),
        new Question("U kojoj zemlji se nalazi Akropolj?",
                "Italija", "Turska", "Grčka", "Albanija", 'C'),
        new Question("Koja je najmanja država u Evropi?",
                "Monako", "San Marino", "Lihtenštajn", "Vatikan", 'D'),
        new Question("Kojom rijekom je okružen Budimpešta?",
                "Sava", "Dunav", "Rajna", "Tiber", 'B'),
        new Question("Koja država ima zastavu s crvenim krstom na bijeloj podlozi?",
                "Danska", "Norveška", "Švajcarska", "Austrija", 'C'),
        new Question("Koji je glavni grad Španije?",
                "Barselona", "Sevilja", "Valensija", "Madrid", 'D'),
        new Question("Na kojoj rijeci leži London?",
                "Sena", "Rajna", "Temza", "Tiber", 'C'),
        new Question("Koji je glavni grad Grčke?",
                "Solun", "Atina", "Sparta", "Korint", 'B'),
        new Question("U kojoj zemlji se nalazi Koloseum?",
                "Španija", "Grčka", "Italija", "Portugal", 'C'),
        new Question("Koji je zvanični jezik Brazila?",
                "Španski", "Engleski", "Francuski", "Portugalski", 'D'),
        new Question("Koliko država graniči s Njemačkom?",
                "7", "8", "9", "10", 'C'),
        new Question("Koji je glavni grad Norveške?",
                "Stokholm", "Helsinki", "Oslo", "Kopenhagen", 'C'),
        new Question("Koji kontinent je pretežno prostorno zauzela Rusija?",
                "Evropa", "Azija", "Arktik", "Podjednako", 'B'),
    };

    private QuestionRepository() {}

    public static QuestionRepository getInstance() {
        if (instance == null) instance = new QuestionRepository();
        return instance;
    }

    public List<Question> getQuestionsForGame() {
        List<Question> pool = new ArrayList<>();
        for (Question q : ALL) pool.add(q);
        Collections.shuffle(pool);
        return pool.subList(0, 5);
    }

    /** Returns questions by the given indices (for shared multiplayer order). */
    public List<Question> getQuestionsByIndices(int[] indices) {
        List<Question> result = new ArrayList<>();
        for (int i : indices) result.add(ALL[i]);
        return result;
    }

    /** Returns a shuffled array of 5 indices into ALL[]. P1 calls this and shares with P2. */
    public int[] generateShuffledIndices() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) pool.add(i);
        Collections.shuffle(pool);
        int[] result = new int[5];
        for (int i = 0; i < 5; i++) result[i] = pool.get(i);
        return result;
    }
}
