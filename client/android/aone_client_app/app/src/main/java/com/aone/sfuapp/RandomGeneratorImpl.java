package com.aone.sfuapp;


import java.util.Random;
import java.util.UUID;

public class RandomGeneratorImpl {

    private final char[] kHex = {'0', '1', '2', '3', '4', '5', '6', '7',
                                 '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    // Version 4 UUID is of the form:
    // xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
    // Where 'x' is a hex digit, and 'y' is 8, 9, a or b.
    public static String CreateRandomUuid() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    public static int CreateRandomId() {
        Random r = new Random();
        int n = r.nextInt();



        return unsigned32(n);
    }

    private static int unsigned32(int n) {
        return n & 0x7FFFFFFF;
    }
}