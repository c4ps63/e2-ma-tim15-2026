package com.example.slagalicavpl.game;

public class MojBrojEngine {

    private MojBrojEngine() {}

    public static int evaluate(String expr) {
        if (expr == null || expr.trim().isEmpty()) return -1;
        try {
            double val = new ExprParser(expr.replaceAll("\\s+", "")).expr();
            if (Double.isNaN(val) || Double.isInfinite(val) || val < 0) return -1;
            return (int) Math.round(val);
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static int[] computeScores(int p1Result, int p2Result, int target, int roundPlayer) {
        int p1pts = 0, p2pts = 0;

        boolean p1Hit = (p1Result == target && p1Result > 0);
        boolean p2Hit = (p2Result == target && p2Result > 0);

        if (p1Hit && p2Hit) {
            if (roundPlayer == 1) p1pts = 10; else p2pts = 10;
        } else if (p1Hit) {
            p1pts = 10;
        } else if (p2Hit) {
            p2pts = 10;
        } else {
            int d1 = (p1Result >= 0) ? Math.abs(p1Result - target) : Integer.MAX_VALUE;
            int d2 = (p2Result >= 0) ? Math.abs(p2Result - target) : Integer.MAX_VALUE;

            if (d1 == Integer.MAX_VALUE && d2 == Integer.MAX_VALUE) {
                // nobody scores
            } else if (d1 < d2) {
                p1pts = 5;
            } else if (d2 < d1) {
                p2pts = 5;
            } else {
                if (roundPlayer == 1) p1pts = 5; else p2pts = 5;
            }
        }
        return new int[]{p1pts, p2pts};
    }

    private static class ExprParser {
        private final String s;
        private int pos = 0;

        ExprParser(String input) { this.s = input; }

        double expr() {
            double v = term();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                v = (op == '+') ? v + term() : v - term();
            }
            return v;
        }

        double term() {
            double v = factor();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                double t = factor();
                if (op == '/' && t == 0) throw new ArithmeticException("div/0");
                v = (op == '*') ? v * t : v / t;
            }
            return v;
        }

        double factor() {
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return v;
            }
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -factor();
            }
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (pos == start)
                throw new NumberFormatException("Expected digit at pos " + pos);
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
