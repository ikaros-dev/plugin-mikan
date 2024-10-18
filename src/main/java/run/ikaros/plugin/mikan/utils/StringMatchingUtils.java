package run.ikaros.plugin.mikan.utils;

public class StringMatchingUtils {
    public static int levenshtein(String s1, String s2) {
        if (s1.length() < s2.length()) {
            return levenshtein(s2, s1);
        }

        if (s2.length() == 0) {
            return s1.length();
        }

        int[] previousRow = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            int[] currentRow = new int[s2.length() + 1];
            currentRow[0] = i;

            for (int j = 1; j <= s2.length(); j++) {
                int insertions = previousRow[j] + 1;
                int deletions = currentRow[j - 1] + 1;
                int substitutions = previousRow[j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1);
                currentRow[j] = Math.min(Math.min(insertions, deletions), substitutions);
            }

            previousRow = currentRow;
        }

        return previousRow[s2.length()];
    }

    public static boolean isSimilar(String s1, String s2, double threshold) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return true; // 两个空字符串视为相似
        }

        int distance = levenshtein(s1, s2);
        double similarity = 1.0 - (double) distance / maxLen;
        return similarity >= threshold;
    }

    public static boolean isSimilar(String s1, String s2) {
        return isSimilar(s1, s2, 0.8);
    }
}
