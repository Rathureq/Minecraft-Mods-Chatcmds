package fr.chatcmds;

import java.util.Locale;

/** Small calculator: + - * / % ^, parentheses, decimals, unary minus. */
public class MathEval {
    private final String s;
    private int pos = -1, ch;

    private MathEval(String s) {
        this.s = s;
    }

    /** Evaluates an expression and returns the chat reply. Never throws. */
    public static String evaluateSafe(String expr) {
        expr = expr.trim();
        if (expr.isEmpty()) return "Usage: !math 2+2";
        if (expr.length() > 100) return "Expression too long.";
        try {
            double r = new MathEval(expr.replace(',', '.')).parse();
            if (Double.isNaN(r)) return "That is not a number.";
            if (Double.isInfinite(r)) {
                return "Infinity and beyond! The calculator gave up.";
            }
            if (Math.abs(r) >= 1e100) {
                int digits = (int) Math.floor(Math.log10(Math.abs(r))) + 1;
                return "Boom! The calculator exploded at " + sci(r) + " (" + digits
                        + " digits). Please insert a new calculator.";
            }
            return expr + " = " + format(r);
        } catch (ArithmeticException e) {
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "Invalid expression.";
        }
    }

    /** Just the value, for the {math:...} placeholder of custom commands ("?" when invalid). */
    static String calc(String expr) {
        try {
            if (expr == null || expr.isBlank() || expr.length() > 100) return "?";
            double r = new MathEval(expr.trim().replace(',', '.')).parse();
            if (Double.isNaN(r) || Double.isInfinite(r)) return "?";
            return Math.abs(r) >= 1e100 ? sci(r) : format(r);
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Plain number for reasonable values, scientific notation for huge or tiny ones. */
    static String format(double v) {
        double a = Math.abs(v);
        if (a >= 1e15 || (a != 0 && a < 1e-8)) return sci(v);
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String t = String.format(Locale.US, "%.8f", v);
        return t.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** 1e300, 1.5e20, -2.5e-12 ... */
    static String sci(double v) {
        String t = String.format(Locale.US, "%.4e", v); // e.g. 1.0000e+300
        int e = t.indexOf('e');
        String mant = t.substring(0, e).replaceAll("0+$", "").replaceAll("\\.$", "");
        int exp = Integer.parseInt(t.substring(e + 1));
        return mant + "e" + exp;
    }

    private void next() {
        ch = (++pos < s.length()) ? s.charAt(pos) : -1;
    }

    private boolean eat(int c) {
        while (ch == ' ') next();
        if (ch == c) {
            next();
            return true;
        }
        return false;
    }

    private double parse() {
        next();
        double x = parseExpression();
        if (pos < s.length()) throw new RuntimeException("unexpected: " + (char) ch);
        return x;
    }

    private double parseExpression() {
        double x = parseTerm();
        for (; ; ) {
            if (eat('+')) x += parseTerm();
            else if (eat('-')) x -= parseTerm();
            else return x;
        }
    }

    private double parseTerm() {
        double x = parseFactor();
        for (; ; ) {
            if (eat('*') || eat('x') || eat('×')) x *= parseFactor();
            else if (eat('/') || eat(':') || eat('÷')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("division by zero");
                x /= d;
            } else if (eat('%')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("division by zero");
                x %= d;
            } else return x;
        }
    }

    private double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();

        double x;
        int start = pos;
        if (eat('(')) {
            x = parseExpression();
            if (!eat(')')) throw new RuntimeException("missing parenthesis");
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
            while ((ch >= '0' && ch <= '9') || ch == '.') next();
            x = Double.parseDouble(s.substring(start, pos));
        } else {
            throw new RuntimeException("unexpected");
        }

        if (eat('^')) {
            double e = parseFactor();
            if (Math.abs(e) > 100000) throw new ArithmeticException("exponent too large, nice try");
            x = Math.pow(x, e);
        }
        return x;
    }
}
