package com.yieldnull.regex;


import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class DFA {

    static class State {
        static final int SPLIT = 256;
        static final int MATCH = 257;

        static int count = 0;

        int c;
        State out;
        State out2;

        int index;

        static State genSplit() {
            return new State(SPLIT);
        }

        static State genMatch() {
            return new State(MATCH);
        }

        State(int c) {
            this.c = c;
            this.index = ++count;
        }

        char genChar() {
            return c < 256 ? (char) c : 'ε';
        }
    }


    static class Frag {
        State start;
        List<State> end;

        Frag(State start, List<State> endStates) {
            this.start = start;
            this.end = endStates;
        }

        Frag(State start, State endState) {
            this.start = start;
            this.end = Collections.singletonList(endState);
        }

        void patch(State target) {
            for (State s : end) {
                if (s.out == null) {
                    s.out = target;
                } else {
                    s.out2 = target;
                }
            }
        }

        static List<State> combine(List<State> list, State state) {
            List<State> end = new LinkedList<>();

            end.addAll(list);
            end.add(state);

            return end;
        }

        static List<State> combine(List<State> list, List<State> list2) {
            List<State> out = new LinkedList<>();

            out.addAll(list);
            out.addAll(list2);

            return out;
        }
    }

    public String format(String regex) {
        StringBuilder builder = new StringBuilder();
        List<Character> operators = Arrays.asList('(', ')', '?', '*', '+', '|');

        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);

            if (c == '(' || !operators.contains(c)) {
                char before = i > 0 ? regex.charAt(i - 1) : '(';

                if (before != '|' && before != '(')
                    builder.append('.');
            }

            builder.append(c);
        }
        return builder.toString();
    }

    public String re2post(String regex) {
        StringBuilder builder = new StringBuilder();
        Stack<Character> stack = new Stack<>();

        Map<Character, Integer> preMap = new HashMap<>(); // precedence map
        preMap.put('+', 4);
        preMap.put('?', 4);
        preMap.put('*', 4);
        preMap.put('.', 3);
        preMap.put('|', 2);
        preMap.put('(', 1);

        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);

            switch (c) {
                case '(':
                    stack.push(c);
                    break;
                case ')':
                    while (stack.peek() != '(') {
                        builder.append(stack.pop());
                    }
                    stack.pop();
                    break;
                case '?':
                case '*':
                case '+':
                case '.':
                case '|':
                    int currentPre = preMap.get(c);

                    int peekPre = !stack.empty() ? preMap.get(stack.peek()) : 0;
                    while (peekPre >= currentPre) {
                        builder.append(stack.pop());
                        peekPre = !stack.empty() ? preMap.get(stack.peek()) : 0;
                    }
                    stack.push(c);
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }

        while (!stack.empty()) {
            builder.append(stack.pop());
        }

        return builder.toString();
    }

    public State post2nfa(String postfix) {
        Stack<Frag> stack = new Stack<>();
        Frag frag, frag1, frag2;
        State split;

        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);
            switch (c) {
                case '.':
                    frag2 = stack.pop();
                    frag1 = stack.pop();

                    frag1.patch(frag2.start);
                    stack.push(new Frag(frag1.start, frag2.end));
                    break;
                case '+':
                    frag = stack.pop();
                    split = State.genSplit();

                    frag.patch(split);
                    split.out = frag.start;
                    stack.push(new Frag(frag.start, split));
                    break;
                case '?':
                    frag = stack.pop();
                    split = State.genSplit();

                    split.out = frag.start;
                    stack.push(new Frag(split, Frag.combine(frag.end, split)));
                    break;
                case '*':
                    frag = stack.pop();
                    split = State.genSplit();

                    frag.patch(split);
                    split.out = frag.start;
                    stack.push(new Frag(split, split));
                    break;
                case '|':
                    frag1 = stack.pop();
                    frag2 = stack.pop();
                    split = State.genSplit();

                    split.out = frag1.start;
                    split.out2 = frag2.start;
                    stack.push(new Frag(split, Frag.combine(frag1.end, frag2.end)));
                    break;
                default:
                    State s = new State(c);
                    stack.push(new Frag(s, s));
                    break;
            }
        }

        frag = stack.pop();
        State match = State.genMatch();
        frag.patch(match);

        return frag.start;
    }

    public void fa2graph(State start) {
        GraphViz gv = new GraphViz();
        List<Integer> visited = new ArrayList<>();

        gv.addln(gv.start_graph());
        gv.addln("rankdir=LR;");
        gv.addln(String.format("node [shape = doublecircle]; S%d S%d;", start.index, State.count));
        gv.addln("node [shape = circle];");

        Queue<State> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            State s = queue.poll();

            if (!visited.contains(s.index)) {
                visited.add(s.index);

                if (s.out != null) {
                    queue.add(s.out);
                    gv.addln(String.format("S%d -> S%d [ label = \"%s\" ];", s.index, s.out.index, s.genChar()));
                }

                if (s.out2 != null) {
                    queue.add(s.out2);
                    gv.addln(String.format("S%d -> S%d [ label = \"%s\" ];", s.index, s.out2.index, s.genChar()));
                }
            }
        }
        gv.addln(gv.end_graph());

        System.out.println(gv.getDotSource());

        String type = "png";
        String representationType = "dot";
        File out = new File("out." + type);
        gv.writeGraphToFile(gv.getGraph(gv.getDotSource(), type, representationType), out);
    }


    public static void main(String[] args) throws IOException {
        DFA dfa = new DFA();

        String regex = "aa*bb*";
        regex = dfa.format(regex);
        regex = dfa.re2post(regex);
        System.out.println(regex);
        State start = dfa.post2nfa(regex);

        dfa.fa2graph(start);

        Desktop.getDesktop().browse(new File("out.png").toURI());
    }
}
