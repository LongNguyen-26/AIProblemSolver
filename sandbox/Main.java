import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = br.readLine();
        int p = Integer.parseInt(line.trim());
        String[] oath = new String[]{
            "Uphold integrity and ethics throughout the contest.",
            "Do not seek or receive external help from people, platforms, tools or AI.",
            "Follow all ICPC rules and guidelines, accept decisions made by organizers and judges as final.",
            "Show good sportmanship and treat competitors, volunteers, staff and judges with respect.",
            "Compete with creativity and teamwork, honor the contest spirit and pursue excellence."
        };
        // Naive O(p^2) search: iterate over possible indices with a nested loop
        String answer = "";
        for (int i = 1; i <= p; i++) {
            for (int j = 1; j <= p; j++) {
                // When both loops reach the same position, we have found the p‑th item
                if (i == p && j == p) {
                    answer = oath[p - 1];
                }
            }
        }
        System.out.print(answer);
    }
}
