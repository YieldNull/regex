package com.yieldnull.regex;


import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Parser {
    private String regex;

    private int nIndex;
    private int dIndex;

    private List<Integer> dMatch = new ArrayList<>();

    private State nStart;
    private State dStart;


    /**
     * A FA(Finite Automaton) State.
     */
    private abstract static class State {

        /**
         * The index of this state
         */
        int index;

        /**
         * All the arrows starting from this state
         */
        List<Arrow> arrows;

        /**
         * Init with a index.
         *
         * @param index the unique state index
         */
        State(int index) {
            this.index = index;
            arrows = new ArrayList<>();
        }

        /**
         * Add an arrow starting from self.
         *
         * @param arrow the arrow
         */
        void addArrow(Arrow arrow) {
            arrows.add(arrow);
        }

        /**
         * Connect to the target state with a dangling arrow which starts from this state.
         *
         * @param target the target state.
         */
        abstract void connect(State target);

        /**
         * Create an Arrow with the Label and points it to the target state from this state.
         *
         * @param label  the label of arrow
         * @param target the target state
         */
        abstract void connect(Label label, State target);

        List<Label> outLabels() {
            return arrows.stream().map(arrow -> arrow.label).collect(Collectors.toList());
        }

        List<State> outStates() {
            return arrows.stream().map(arrow -> arrow.target).collect(Collectors.toList());
        }

        boolean contains(Label label) {
            return arrows.stream().anyMatch(arrow -> arrow.label.equals(label));
        }

        List<State> findOutStates(Label label) {
            return arrows.stream().filter(arrow -> arrow.label.equals(label)).map(arrow -> arrow.target).collect(Collectors.toList());
        }
    }

    /**
     * A NFA(Nondeterministic Fnite Automaton) State
     */
    private static class NFAState extends State {
        NFAState(int index) {
            super(index);
        }


        @Override
        void connect(State target) {
            for (Arrow arrow : arrows) {
                if (arrow.target == null) {
                    arrow.target = target;
                    break;
                }
            }
        }

        @Override
        void connect(Label label, State target) {
            addArrow(new Arrow(label, target));
        }

    }

    /**
     * A DFA(Deterministic Finite Automaton) State
     */
    private static class DFAState extends State {

        Set<State> states;


        DFAState(int index, Set<State> states) {
            super(index);
            this.states = states;
        }

        @Override
        void connect(State target) {

        }

        @Override
        void connect(Label label, State target) {
            addArrow(new Arrow(label, target));
        }

    }

    /**
     * The Arrow that connects two States.
     */
    private static class Arrow {

        Label label;
        State target;

        Arrow(Label label) {
            this.label = label;
        }

        Arrow(Label label, State target) {
            this(label);
            this.target = target;
        }
    }

    /**
     * The label of Arrow.
     */
    private abstract static class Label {
        static final int TYPE_CHAR = 0;
        static final int TYPE_EMPTY = 1;

        int type;

        Label(int type) {
            this.type = type;
        }


        public abstract String toString();

        public boolean equals(Object obj) {
            return obj instanceof Label && obj.toString().equals(toString());
        }
    }

    /**
     * A single character Label
     */
    private static class CharLabel extends Label {
        char chr;

        CharLabel(char chr) {
            super(TYPE_CHAR);
            this.chr = chr;
        }

        @Override
        public String toString() {
            if (chr != 'ε') {
                return String.valueOf(chr);
            } else {
                return "\\" + chr;
            }
        }
    }

    /**
     * An Empty('ε') Label.
     */
    private static class EmptyLabel extends Label {

        EmptyLabel() {
            super(TYPE_EMPTY);
        }


        @Override
        public String toString() {
            return "ε";
        }
    }

    /**
     * A NFA Fragment which consists of some NFA States and Arrows.
     */
    private static class NFAFrag {
        State start;
        List<State> end;

        NFAFrag(State start, List<State> endStates) {
            this.start = start;
            this.end = endStates;
        }

        NFAFrag(State start, State end) {
            this.start = start;
            this.end = Collections.singletonList(end);
        }

        void connect(State target) {
            for (State s : end) {
                s.arrows.stream().filter(r -> r.target == null).forEach(r -> r.target = target);
            }
        }

        static List<State> combine(NFAFrag frag, State state) {
            List<State> end = new LinkedList<>(frag.end);
            end.add(state);

            return end;
        }

        static List<State> combine(NFAFrag frag1, NFAFrag frag2) {
            List<State> out = new LinkedList<>();

            out.addAll(frag1.end);
            out.addAll(frag2.end);

            return out;
        }
    }


    /**
     * Generate a NFA State.
     *
     * @return a NFA State
     */
    private NFAState genNState() {
        return genNState(null);
    }

    /**
     * Generate a NFA State with an dangling Arrow.
     *
     * @param label the Label of Arrow
     * @return a NFA State
     */
    private NFAState genNState(Label label) {
        return genNState(label, null);
    }

    /**
     * Generate a NFA State with an Arrow that points to the target state.
     *
     * @param label  the Label of Arrow
     * @param target the State which the Arrow points to.
     * @return a NFA State
     */
    private NFAState genNState(Label label, State target) {
        NFAState nState = new NFAState(++nIndex);

        Arrow arrow = null;

        if (label != null) {
            arrow = new Arrow(label);
            nState.addArrow(arrow);
        }

        if (target != null) {
            assert arrow != null;
            arrow.target = target;
        }


        return nState;
    }

    /**
     * Generate a NFA State with two dangling Arrow whose label is Empty.
     *
     * @return a split NFA State
     */
    private NFAState genNSplitState() {
        NFAState state = genNState(new EmptyLabel());
        state.addArrow(new Arrow(new EmptyLabel()));

        return state;
    }

    /**
     * Generate a DFA State.
     *
     * @param states The set of NFA indexes
     * @return a DFA State
     */
    private DFAState genDState(Set<State> states) {
        DFAState dState = new DFAState(++dIndex, states);
        if (states.stream().anyMatch(state -> state.index == nIndex)) {
            dMatch.add(dState.index);
        }

        return dState;
    }

    /**
     * Transform regular expression by inserting a '.' as explicit concatenation operator.
     *
     * @param regex the regular expression
     * @return a formatted regex
     */
    private String format(String regex) {
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

    /**
     * Convert regular expression from infix to postfix notation using Shunting-yard algorithm.
     *
     * @param regex the regular expression
     * @return a converted regex
     */
    private String re2post(String regex) {
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

    /**
     * Convert postfix regular expression to NFA.
     *
     * @param postfix the postfix regular expression
     * @return the start state
     */
    private State post2nfa(String postfix) {
        Stack<NFAFrag> stack = new Stack<>();
        NFAFrag frag, frag1, frag2;
        State split;

        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);
            switch (c) {
                case '.':
                    frag2 = stack.pop();
                    frag1 = stack.pop();

                    frag1.connect(frag2.start);
                    stack.push(new NFAFrag(frag1.start, frag2.end));
                    break;
                case '+':
                    frag = stack.pop();
                    split = genNSplitState();

                    frag.connect(split);
                    split.connect(frag.start);
                    stack.push(new NFAFrag(frag.start, split));
                    break;
                case '?':
                    frag = stack.pop();
                    split = genNSplitState();

                    split.connect(frag.start);
                    stack.push(new NFAFrag(split, NFAFrag.combine(frag, split)));
                    break;
                case '*':
                    frag = stack.pop();
                    split = genNSplitState();

                    frag.connect(split);
                    split.connect(frag.start);
                    stack.push(new NFAFrag(split, split));
                    break;
                case '|':
                    frag1 = stack.pop();
                    frag2 = stack.pop();
                    split = genNSplitState();

                    split.connect(frag1.start);
                    split.connect(frag2.start);
                    stack.push(new NFAFrag(split, NFAFrag.combine(frag1, frag2)));
                    break;
                default:
                    State state = genNState(new CharLabel(c));
                    stack.push(new NFAFrag(state, state));
                    break;
            }
        }

        frag = stack.pop();
        State match = genNState();
        frag.connect(match);

        return frag.start;
    }


    private State nfa2dfa(State start) {
        DFAState s0 = genDState(closure(Collections.singleton(start)));

        Stack<DFAState> stack = new Stack<>();
        stack.push(s0);

        LinkedList<DFAState> visited = new LinkedList<>();

        while (!stack.empty()) {
            DFAState state = stack.pop();
            visited.add(state);

            Set<State> states = state.states;

            states.stream()
                    .flatMap(s -> s.outLabels().stream())
                    .distinct()
                    .filter(label -> !(label instanceof EmptyLabel)).forEach(label -> {

                // ε-closure(move(states, c))
                Set<State> cloMove = closure(states.stream()
                        .flatMap(s -> s.findOutStates(label).stream())
                        .collect(Collectors.toSet()));

                DFAState right = null;
                for (DFAState s : visited) {
                    if (s.states.equals(cloMove)) {
                        right = s;
                        break;
                    }
                }

                if (right == null) {
                    right = genDState(cloMove);
                    stack.add(right);
                }

                state.connect(label, right);
            });
        }

        return s0;
    }

    private Set<State> closure(Set<State> set) {
        Set<State> closure = new HashSet<>(set);

        Stack<State> stack = new Stack<>();
        stack.addAll(set);

        while (!stack.empty()) {
            State state = stack.pop();

            state.arrows.stream()
                    .filter(arrow -> arrow.label instanceof EmptyLabel && !closure.contains(arrow.target))
                    .forEach(arrow -> {
                        closure.add(arrow.target);
                        stack.add(arrow.target);
                    });
        }

        return closure;
    }


    public void drawNFA() {
        draw(nStart, Collections.singletonList(nIndex), false);
    }

    public void drawDFA() {
        draw(dStart, dMatch, true);
    }

    private void draw(State start, List<Integer> matchedIndex, boolean isDFA) {
        GraphViz gv = new GraphViz();
        gv.addln(gv.start_graph());
        gv.addln("rankdir=LR;");

        gv.add("node [shape = doublecircle]; ");
        matchedIndex.forEach(i -> gv.add(" S" + i));
        gv.addln(" ;");

        gv.addln("node [shape = circle];");
        gv.addln("S0 [style = invis]");
        gv.addln(String.format("S0 -> S%d [ label = \"start\"]", dStart.index));


        List<Integer> visited = new ArrayList<>();
        Queue<State> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            State state = queue.poll();

            if (!visited.contains(state.index)) {
                visited.add(state.index);

                state.arrows.forEach(arrow -> {
                    queue.add(arrow.target);

                    gv.addln(String.format("S%d -> S%d [ label = \"%s\" ];", state.index, arrow.target.index, arrow.label));
                });
            }
        }

        gv.addln(gv.end_graph());

        String type = "png";
        String representationType = "dot";
        String fa = isDFA ? "DFA" : "NFA";

        File out = new File(String.format("%s.%s", fa, type));
        gv.writeGraphToFile(gv.getGraph(gv.getDotSource(), type, representationType), out);
    }

    public void compile(String re) {
        regex = re;

        re = format(re);
        System.out.println("Formatted: " + re);

        re = re2post(re);
        System.out.println("Postfix: " + re);

        nStart = post2nfa(re);
        dStart = nfa2dfa(nStart);
    }

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();

        System.out.print("Regex: ");
        String regex = new Scanner(System.in).nextLine();

        parser.compile(regex);

        parser.drawNFA();
        parser.drawDFA();

        Desktop.getDesktop().browse(new File("NFA.png").toURI());
        Desktop.getDesktop().browse(new File("DFA.png").toURI());
    }
}
