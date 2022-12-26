package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        List<Integer> myList = new ArrayList<Integer>();
        for (int i=0; i<10; i++)
            myList.add(i);
        Random rnd = new Random(0);
        for (int i=0; i<10; i++) {
            Collections.shuffle(myList, rnd);
            System.out.println(myList);
        }

    }
}