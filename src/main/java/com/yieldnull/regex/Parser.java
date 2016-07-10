package com.yieldnull.regex;


import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Parser {

    static class NState {
        static final int SPLIT = 256;
        static final int MATCH = 257;

        static int count = 0;

        int c;
        NState out;
        NState out2;

        int index;

        static NState genSplit() {
            return new NState(SPLIT);
        }

        static NState genMatch() {
            return new NState(MATCH);
        }

        NState(int c) {
            this.c = c;
            this.index = ++count;
        }

        char genChar() {
            return c < 256 ? (char) c : 'ε';
        }

        boolean isSplit() {
            return c == SPLIT;
        }
    }


    static class NFrag {
        NState start;
        List<NState> end;

        NFrag(NState start, List<NState> endStates) {
            this.start = start;
            this.end = endStates;
        }

        NFrag(NState start, NState endState) {
            this.start = start;
            this.end = Collections.singletonList(endState);
        }

        void patch(NState target) {
            for (NState s : end) {
                if (s.out == null) {
                    s.out = target;
                } else {
                    s.out2 = target;
                }
            }
        }

        static List<NState> combine(List<NState> list, NState state) {
            List<NState> end = new LinkedList<>();

            end.addAll(list);
            end.add(state);

            return end;
        }

        static List<NState> combine(List<NState> list, List<NState> list2) {
            List<NState> out = new LinkedList<>();

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

    public NState post2nfa(String postfix) {
        Stack<NFrag> stack = new Stack<>();
        NFrag frag, frag1, frag2;
        NState split;

        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);
            switch (c) {
                case '.':
                    frag2 = stack.pop();
                    frag1 = stack.pop();

                    frag1.patch(frag2.start);
                    stack.push(new NFrag(frag1.start, frag2.end));
                    break;
                case '+':
                    frag = stack.pop();
                    split = NState.genSplit();

                    frag.patch(split);
                    split.out = frag.start;
                    stack.push(new NFrag(frag.start, split));
                    break;
                case '?':
                    frag = stack.pop();
                    split = NState.genSplit();

                    split.out = frag.start;
                    stack.push(new NFrag(split, NFrag.combine(frag.end, split)));
                    break;
                case '*':
                    frag = stack.pop();
                    split = NState.genSplit();

                    frag.patch(split);
                    split.out = frag.start;
                    stack.push(new NFrag(split, split));
                    break;
                case '|':
                    frag1 = stack.pop();
                    frag2 = stack.pop();
                    split = NState.genSplit();

                    split.out = frag1.start;
                    split.out2 = frag2.start;
                    stack.push(new NFrag(split, NFrag.combine(frag1.end, frag2.end)));
                    break;
                default:
                    NState s = new NState(c);
                    stack.push(new NFrag(s, s));
                    break;
            }
        }

        frag = stack.pop();
        NState match = NState.genMatch();
        frag.patch(match);

        return frag.start;
    }


    private static class DArrow {
        char label;
        DState end;

        DArrow(char label, DState end) {
            this.label = label;
            this.end = end;
        }
    }


    static class DState {
        static int count;
        static List<Integer> match = new ArrayList<>();

        int index;
        List<DArrow> out;

        Set<NState> states;


        DState(Set<NState> states) {
            this();
            this.states = states;
            this.out = new LinkedList<>();

            if (this.states.stream().anyMatch(s -> s.index == NState.count)) {
                match.add(this.index);
            }

        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DState && ((DState) obj).states.equals(states);
        }

        private DState() {
            this.index = ++count;
        }

        void connect(Character c, DState right) {
            out.add(new DArrow(c, right));
        }

        void destroy() {
            count--;
            match.remove(Integer.valueOf(index));
        }

    }

    public DState nfa2dfa(NState start) {
        DState s0 = new DState(closure(Collections.singleton(start)));

        Stack<DState> stack = new Stack<>();
        stack.push(s0);

        LinkedList<DState> visited = new LinkedList<>();

        while (!stack.empty()) {
            DState left = stack.pop();
            visited.add(left);

            Set<NState> T = left.states;

            T.stream().map(s -> (char) s.c).distinct()
                    .filter(c -> c != NState.SPLIT && c != NState.MATCH).forEach(c -> {

                // ε-closure(move(T, c))
                Set<NState> cloMove = closure(T.stream()
                        .filter(s -> s.genChar() == c)
                        .map(s -> s.out)
                        .collect(Collectors.toSet()));

                DState right = new DState(cloMove);
                int index = visited.indexOf(right);

                if (index == -1) {
                    left.connect(c, right);
                    stack.add(right);
                } else {
                    right.destroy();
                    left.connect(c, visited.get(index));
                }
            });
        }

        return s0;
    }

    private Set<NState> closure(Set<NState> set) {
        Set<NState> closure = new HashSet<>(set);

        Stack<NState> stack = new Stack<>();
        stack.addAll(set);

        while (!stack.empty()) {
            NState state = stack.pop();

            if (state.isSplit()) {
                Arrays.asList(state.out, state.out2).stream()
                        .filter(s -> !closure.contains(s))
                        .forEach(s -> {
                            closure.add(s);
                            stack.add(s);
                        });
            }
        }

        return closure;
    }


    public void drawNFA(NState start) {
        GraphViz gv = new GraphViz();
        gv.addln(gv.start_graph());
        gv.addln("rankdir=LR;");

        gv.addln(String.format("node [shape = doublecircle]; S%d;", NState.count));
        gv.addln("node [shape = circle];");

        gv.addln("S0 [style = invis]");
        gv.addln(String.format("S0 -> S%d [ label = \"start\"]", start.index));

        List<Integer> visited = new ArrayList<>();
        Queue<NState> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            NState s = queue.poll();

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

        draw(gv, false);
    }

    public void drawDFA(DState start) {
        GraphViz gv = new GraphViz();
        gv.addln(gv.start_graph());
        gv.addln("rankdir=LR;");

        gv.add("node [shape = doublecircle]; ");
        DState.match.forEach(i -> gv.add(" S" + i));
        gv.addln(" ;");

        gv.addln("node [shape = circle];");
        gv.addln("S0 [style = invis]");
        gv.addln(String.format("S0 -> S%d [ label = \"start\"]", start.index));

        List<Integer> visited = new ArrayList<>();
        Queue<DState> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            DState s = queue.poll();

            if (!visited.contains(s.index)) {
                visited.add(s.index);

                s.out.forEach(r -> {
                    queue.add(r.end);

                    gv.addln(String.format("S%d -> S%d [ label = \"%s\" ];", s.index, r.end.index, r.label));
                });
            }
        }

        gv.addln(gv.end_graph());
        draw(gv, true);
    }

    private void draw(GraphViz gv, boolean isDFA) {

        String type = "png";
        String representationType = "dot";
        String fa = isDFA ? "DFA" : "NFA";

        File out = new File(String.format("%s.%s", fa, type));
        gv.writeGraphToFile(gv.getGraph(gv.getDotSource(), type, representationType), out);
    }

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();

        System.out.print("Regex: ");
        String regex = new Scanner(System.in).nextLine();

        regex = parser.format(regex);
        System.out.println("Formatted: " + regex);

        regex = parser.re2post(regex);
        System.out.println("Postfix: " + regex);

        NState nStart = parser.post2nfa(regex);
        DState dStart = parser.nfa2dfa(nStart);

        parser.drawNFA(nStart);
        parser.drawDFA(dStart);

        Desktop.getDesktop().browse(new File("NFA.png").toURI());
        Desktop.getDesktop().browse(new File("DFA.png").toURI());
    }
}
