/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.mixin.injection.callback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.Break;
import org.spongepowered.asm.mixin.injection.Continue;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.InjectionPoint.RestrictTargetLevel;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.code.InjectorTarget;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointAnnotationContext;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.injection.struct.Target.Extension;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Locals;

/**
 * Injects a handler call that leaves a loop around the injection point or skips
 * to its next iteration, see {@link Break} and {@link Continue}.
 */
public class LoopJumpInjector extends Injector {

    private static final String[] SELECTORS = { "loop", "to" };

    private final boolean toNextIteration;

    private final Map<String, List<InjectionPoint>> selectors = new HashMap<String, List<InjectionPoint>>();

    private final Map<Target, Map<String, InjectionNode>> selected = new HashMap<Target, Map<String, InjectionNode>>();

    private final Map<Target, Set<AbstractInsnNode>> original = new HashMap<Target, Set<AbstractInsnNode>>();

    public LoopJumpInjector(InjectionInfo info, String annotationType, boolean toNextIteration) {
        super(info, annotationType);
        this.toNextIteration = toNextIteration;
        for (String key : LoopJumpInjector.SELECTORS) {
            List<AnnotationNode> ats = Annotations.<AnnotationNode>getValue(info.getAnnotationNode(), key, false);
            if (ats != null && !ats.isEmpty()) {
                List<InjectionPoint> points = new ArrayList<InjectionPoint>();
                for (AnnotationNode at : ats) {
                    points.add(InjectionPoint.parse(new InjectionPointAnnotationContext(info, at, key), at));
                }
                this.selectors.put(key, points);
            }
        }
    }

    // Selectors are resolved alongside the injection points, before other injectors add jumps that would shift their
    // ordinals
    @Override
    protected void addTargetNode(InjectorTarget injectorTarget, List<InjectionNode> myNodes, AbstractInsnNode node, Set<InjectionPoint> nominators) {
        super.addTargetNode(injectorTarget, myNodes, node, nominators);
        Target target = injectorTarget.getTarget();
        if (this.selected.containsKey(target)) {
            return;
        }
        this.original.put(target, new HashSet<AbstractInsnNode>(Arrays.asList(target.insns.toArray())));
        Map<String, InjectionNode> nodes = new HashMap<String, InjectionNode>();
        for (Map.Entry<String, List<InjectionPoint>> selector : this.selectors.entrySet()) {
            List<AbstractInsnNode> matches = new ArrayList<AbstractInsnNode>();
            for (InjectionPoint point : selector.getValue()) {
                point.find(target.getDesc(), target.insns, matches);
            }
            if (matches.size() != 1) {
                throw new InvalidInjectionException(this.info, String.format("%s '%s' matched %d instructions in %s, expected one",
                        this.annotationType, selector.getKey(), matches.size(), target));
            }
            nodes.put(selector.getKey(), target.addInjectionNode(matches.get(0)));
        }
        this.selected.put(target, nodes);
    }

    @Override
    protected void inject(Target target, InjectionNode node) {
        this.checkTargetForNode(target, node, RestrictTargetLevel.ALLOW_ALL);
        InjectorData handler = new InjectorData(target, "loop handler");
        this.validateParams(handler, Type.BOOLEAN_TYPE);

        InsnList insns = target.insns;
        int at = insns.indexOf(node.getCurrentTarget());
        Map<String, InjectionNode> selected = this.selected.get(target);
        AbstractInsnNode loopInsn = selected.containsKey("loop") ? selected.get("loop").getCurrentTarget() : node.getCurrentTarget();
        int toIndex = selected.containsKey("to") ? insns.indexOf(selected.get("to").getCurrentTarget()) : -1;

        // Loops only exist as backward jumps. javac and ecj lay each loop out contiguously from its header, the
        // backward jump's target, to its last backward jump.
        int[] latches = new int[insns.size()];
        Arrays.fill(latches, -1);
        for (int i = 0; i < latches.length; i++) {
            if (insns.get(i) instanceof JumpInsnNode) {
                int dest = insns.indexOf(((JumpInsnNode) insns.get(i)).label);
                if (dest < i) {
                    latches[dest] = Math.max(latches[dest], i);
                }
            }
        }

        // The loop is the innermost one around the selected instruction, and around 'to' when given
        int first = insns.indexOf(loopInsn);
        int last = Math.max(first, toIndex);
        if (toIndex >= 0) {
            first = Math.min(first, toIndex);
        }
        int header = first;
        while (header >= 0 && latches[header] < last) {
            header--;
        }
        if (header < 0) {
            throw new InvalidInjectionException(this.info, String.format(toIndex < 0 ? "%s selected an instruction outside of any loop in %s"
                    : "%s found no loop around both the selected instruction and 'to' in %s", this.annotationType, target));
        }
        int latch = latches[header];
        if (at < header || at > latch) {
            throw new InvalidInjectionException(this.info, String.format("%s injection point is outside the selected loop in %s",
                    this.annotationType, target));
        }

        final List<Set<Integer>> successors = new ArrayList<Set<Integer>>();
        for (int i = 0; i < insns.size(); i++) {
            successors.add(new HashSet<Integer>());
        }
        Frame<BasicValue>[] frames;
        try {
            frames = new Analyzer<BasicValue>(new BasicInterpreter()) {
                @Override
                protected void newControlFlowEdge(int insn, int successor) {
                    successors.get(insn).add(successor);
                }
            }.analyze(target.classNode.name, target.method);
        } catch (AnalyzerException ex) {
            throw new InvalidInjectionException(this.info, String.format("%s could not analyse %s: %s", this.annotationType, target, ex.getMessage()));
        }
        if (frames[at] == null) {
            throw new InvalidInjectionException(this.info, String.format("%s selected unreachable code in %s", this.annotationType, target));
        }

        int destination;
        if (this.toNextIteration) {
            destination = this.findNextIteration(target, frames, successors, at, toIndex, header, latch);
        } else {
            // A selected jump out of the loop, such as the condition's exit test, says exactly where the loop ends
            int exit = loopInsn instanceof JumpInsnNode ? insns.indexOf(((JumpInsnNode) loopInsn).label) : header;
            destination = exit < header || exit > latch ? exit : this.findExit(target, header, latch);
        }

        // Code other injectors added to the rest of the iteration would stop running, so leave the loop alone and let
        // require decide whether that is fatal. A lower order applies this injector before them instead.
        Set<AbstractInsnNode> original = this.original.get(target);
        Set<Integer> skipped = new TreeSet<Integer>();
        LoopJumpInjector.reachesNextIteration(successors, at, Collections.<Integer>emptySet(), header, this.toNextIteration ? destination : latch + 1,
                latch, skipped);
        String skippedCode = null;
        for (int index : skipped) {
            AbstractInsnNode insn = insns.get(index);
            if (insn.getOpcode() >= 0 && !original.contains(insn)) {
                if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(target.classNode.name)) {
                    skippedCode = "the call to " + ((MethodInsnNode) insn).name;
                    break;
                }
                skippedCode = "code";
            }
        }
        if (skippedCode != null) {
            String message = String.format("%s in %s did not apply because its jump would skip %s that another injector added. Lower its order "
                    + "to apply it first and skip that code anyway", this.annotationType, target, skippedCode);
            Injector.logger.warn("{}", message);
            this.info.addMessage("%s", message);
            return;
        }

        for (TryCatchBlockNode block : target.method.tryCatchBlocks) {
            int start = insns.indexOf(block.start);
            int end = insns.indexOf(block.end);
            if (block.type == null && start <= at && at < end && (destination < start || destination >= end)) {
                throw new InvalidInjectionException(this.info, String.format("%s in %s would leave a finally or synchronized block without running it",
                        this.annotationType, target));
            }
        }

        Frame<BasicValue> from = frames[at];
        Frame<BasicValue> to = frames[destination];
        if (to == null) {
            throw new InvalidInjectionException(this.info, String.format("%s selected unreachable code in %s", this.annotationType, target));
        }

        InsnList code = new InsnList();
        Extension extraStack = target.extendStack();
        if (!this.isStatic) {
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            extraStack.add();
        }
        this.pushArgs(target.arguments, code, target.getArgIndices(), 0, handler.captureTargetArgs, extraStack);
        this.invokeHandler(code);
        extraStack.set(Math.max(extraStack.get(), 1)).apply();

        AbstractInsnNode destinationInsn = insns.get(destination);
        LabelNode label = destinationInsn instanceof LabelNode ? (LabelNode) destinationInsn : new LabelNode();
        if (from.getStackSize() == to.getStackSize()) {
            code.add(new JumpInsnNode(Opcodes.IFNE, label));
        } else {
            // The destination starts a statement, so drop the operands already pushed for the selected instruction
            LabelNode resume = new LabelNode();
            code.add(new JumpInsnNode(Opcodes.IFEQ, resume));
            for (int i = from.getStackSize() - 1; i >= to.getStackSize(); i--) {
                code.add(new InsnNode(from.getStack(i).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
            }
            code.add(new JumpInsnNode(Opcodes.GOTO, label));
            code.add(resume);
        }
        Collections.addAll(original, code.toArray());
        target.insertBefore(node, code);
        if (label != destinationInsn) {
            insns.insertBefore(destinationInsn, label);
        }

        // The new edge merges into existing code, so a local the destination reads may now be unassigned on one path
        try {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(target.classNode.name, target.method);
        } catch (AnalyzerException ex) {
            throw new InvalidInjectionException(this.info, String.format("%s in %s produces unverifiable code, likely a local that is read after the "
                    + "jump but not yet assigned where the handler is called: %s", this.annotationType, target, ex.getMessage()));
        }
    }

    private int findExit(Target target, int header, int latch) {
        InsnList insns = target.insns;
        JumpInsnNode firstJump = null;
        Set<Integer> destinations = new HashSet<Integer>();
        for (int i = header; i <= latch; i++) {
            if (insns.get(i) instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) insns.get(i);
                int destination = insns.indexOf(jump.label);
                if (destination < header || destination > latch) {
                    destinations.add(destination);
                }
                if (firstJump == null) {
                    firstJump = jump;
                }
            }
        }
        if (insns.get(latch).getOpcode() != Opcodes.GOTO) {
            destinations.add(latch + 1);
        }

        // Labels sitting at the same instruction are the same exit
        Map<AbstractInsnNode, Integer> exits = new LinkedHashMap<AbstractInsnNode, Integer>();
        for (int destination : destinations) {
            AbstractInsnNode insn = insns.get(destination);
            while (insn.getOpcode() < 0) {
                insn = insn.getNext();
            }
            exits.put(insn, destination);
        }
        if (exits.isEmpty()) {
            throw new InvalidInjectionException(this.info, String.format("%s in %s has no exit to jump to, since the loop only ends through return "
                    + "or throw", this.annotationType, target));
        }
        if (exits.size() == 1) {
            return exits.values().iterator().next();
        }

        // Labelled break and continue add exits. A loop tested first leaves through its condition's jump, and a loop
        // ending in a conditional jump (do-while, or while (true) ending in "if (...) break;") falls out of it. The
        // condition goes first because javac also places the body of a loop's last if after a conditional backward
        // jump, where it looks like a fall-through exit.
        if (firstJump.getOpcode() != Opcodes.GOTO && destinations.contains(insns.indexOf(firstJump.label))) {
            return insns.indexOf(firstJump.label);
        }
        if (insns.get(latch).getOpcode() != Opcodes.GOTO) {
            return latch + 1;
        }
        throw new InvalidInjectionException(this.info, String.format("%s found %d exits for the loop in %s, select the exit jump with 'loop'",
                this.annotationType, exits.size(), target));
    }

    private int findNextIteration(Target target, Frame<BasicValue>[] frames, List<Set<Integer>> successors, int at, int toIndex, int header,
            int latch) {
        InsnList insns = target.insns;
        Set<AbstractInsnNode> original = this.original.get(target);
        boolean bottomTested = insns.get(latch).getOpcode() != Opcodes.GOTO;
        int base = frames[header].getStackSize();
        int destination = latch;
        if (toIndex >= 0) {
            // 'to' usually matches an instruction partway through a statement, so the jump lands where that statement
            // starts, the nearest point before it with the stack back at the loop's base height
            destination = toIndex;
            while (insns.get(destination).getOpcode() < 0) {
                destination++;
            }
            if (frames[destination] == null) {
                throw new InvalidInjectionException(this.info, String.format("%s 'to' selected unreachable code in %s", this.annotationType, target));
            }
            while (insns.get(destination).getOpcode() < 0 || frames[destination] == null || frames[destination].getStackSize() != base) {
                destination--;
            }
            if (destination <= at) {
                throw new InvalidInjectionException(this.info, String.format("%s 'to' must select a statement after the injection point in %s",
                        this.annotationType, target));
            }
            AbstractInsnNode previous = insns.get(destination).getPrevious();
            while (previous.getOpcode() < 0) {
                previous = previous.getPrevious();
            }
            if (previous instanceof JumpInsnNode && previous.getOpcode() != Opcodes.GOTO) {
                int skip = insns.indexOf(((JumpInsnNode) previous).label);
                if (skip < insns.indexOf(previous) || skip > destination) {
                    throw new InvalidInjectionException(this.info, String.format("%s 'to' in %s lands partway through an if statement or a condition "
                            + "joined with && or ||. Select the first JUMP of its test instead", this.annotationType, target));
                }
            }
        } else if (bottomTested) {
            throw new InvalidInjectionException(this.info, String.format("%s cannot find where the condition starts in a loop tested at the end, "
                    + "such as do-while, in %s. Select the condition with 'to'", this.annotationType, target));
        }

        // A loop tested first runs its condition from the header to its last conditional jump out of the loop, since
        // "a && b" has one per operand. Without one, as in while (true), the first jump stands in for it. A loop tested
        // at the end runs its condition from where 'to' lands up to the backward jump.
        int conditionStart = header;
        int conditionEnd = header;
        if (bottomTested) {
            conditionStart = destination;
            conditionEnd = latch;
        } else {
            while (!(insns.get(conditionEnd) instanceof JumpInsnNode)) {
                conditionEnd++;
            }
            for (int i = conditionEnd; i <= latch; i++) {
                if (insns.get(i) instanceof JumpInsnNode && insns.get(i).getOpcode() != Opcodes.GOTO) {
                    int dest = insns.indexOf(((JumpInsnNode) insns.get(i)).label);
                    if (dest < header || dest > latch) {
                        conditionEnd = i;
                    }
                }
            }
        }
        Set<String> conditionState = new HashSet<String>();
        for (int i = conditionStart; i < conditionEnd; i++) {
            AbstractInsnNode insn = insns.get(i);
            if (insn.getOpcode() >= Opcodes.ILOAD && insn.getOpcode() <= Opcodes.ALOAD) {
                conditionState.add("local " + ((VarInsnNode) insn).var);
            } else if (insn.getOpcode() == Opcodes.GETFIELD || insn.getOpcode() == Opcodes.GETSTATIC) {
                conditionState.add("field " + ((FieldInsnNode) insn).owner + "." + ((FieldInsnNode) insn).name);
            }
        }

        // Bytecode does not record where a for loop's body ends and its update begins, and no @At can select a counter
        // such as i++ or i += step since it only uses locals. So counters right before the destination that write a
        // variable the condition reads, and run on every path to it, run with the jump. Code other injectors added
        // between them, such as a @ModifyVariable on the counter, runs with them.
        int end = destination;
        boolean writes = false;
        for (int i = destination - 1; i > at; i--) {
            if (!original.contains(insns.get(i))) {
                continue;
            }
            int opcode = insns.get(i).getOpcode();
            if (!(opcode <= Opcodes.ALOAD || opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE || opcode >= Opcodes.DUP && opcode <= Opcodes.DCMPG)) {
                break;
            }
            writes |= conditionState.contains(LoopJumpInjector.written(insns.get(i)));
            if (opcode >= 0 && frames[i] != null && frames[i].getStackSize() == base) {
                if (writes && LoopJumpInjector.reachesNextIteration(successors, at, Collections.singleton(i), header, end, latch,
                        new HashSet<Integer>())) {
                    break;
                }
                if (writes) {
                    destination = i;
                }
                writes = false;
            }
        }

        // Skipping the writes the condition reads could loop forever, unless some path from the injection point to the
        // next iteration already goes without them, as when found = true sits behind an if. Writes other injectors
        // added are left to the check in inject, which backs off instead of failing.
        Set<Integer> skipped = new TreeSet<Integer>();
        LoopJumpInjector.reachesNextIteration(successors, at, Collections.<Integer>emptySet(), header, destination, latch, skipped);
        Set<Integer> conditionWrites = new TreeSet<Integer>();
        for (int index : skipped) {
            if (original.contains(insns.get(index)) && conditionState.contains(LoopJumpInjector.written(insns.get(index)))) {
                conditionWrites.add(index);
            }
        }
        if (!conditionWrites.isEmpty()
                && !LoopJumpInjector.reachesNextIteration(successors, at, conditionWrites, header, destination, latch, new HashSet<Integer>())) {
            int write = conditionWrites.iterator().next();
            int statement = write;
            while (insns.get(statement).getOpcode() < 0 || frames[statement] == null || frames[statement].getStackSize() != base) {
                statement--;
            }
            throw new InvalidInjectionException(this.info, String.format("%s in %s would skip %s. Every path from the injection point to the next "
                    + "iteration writes a variable the loop condition reads, so skipping them could loop forever. %s", this.annotationType, target,
                    LoopJumpInjector.describeWrite(target, insns.get(write)), statement <= at
                    ? "The injection point is inside the statement that writes it, so select another statement with 'at'"
                    : "Select the statement that writes it with 'to', or the statement after it if it only uses locals"));
        }
        return destination;
    }

    // Follows the paths from the injection point that stay in the loop and avoid some instructions, collecting what they
    // run, and says whether any of them reaches the next iteration
    private static boolean reachesNextIteration(List<Set<Integer>> successors, int at, Set<Integer> avoid, int header, int destination,
            int latch, Set<Integer> seen) {
        boolean reached = false;
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(at);
        while (!queue.isEmpty()) {
            int i = queue.poll();
            if (i == header || i >= destination && i <= latch) {
                reached = true;
            } else if (!avoid.contains(i) && i > header && i < destination && seen.add(i)) {
                queue.addAll(successors.get(i));
            }
        }
        return reached;
    }

    private static String written(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == Opcodes.IINC ? "local " + ((IincInsnNode) insn).var
                : opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE ? "local " + ((VarInsnNode) insn).var
                : opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC ? "field " + ((FieldInsnNode) insn).owner + "." + ((FieldInsnNode) insn).name
                : null;
    }

    private static String describeWrite(Target target, AbstractInsnNode insn) {
        String state = LoopJumpInjector.written(insn);
        if (state.startsWith("local ")) {
            int var = insn instanceof IincInsnNode ? ((IincInsnNode) insn).var : ((VarInsnNode) insn).var;
            LocalVariableNode local = Locals.getLocalVariableAt(target.classNode, target.method, insn, var);
            if (local != null) {
                state = "local '" + local.name + "'";
            }
        }
        for (AbstractInsnNode line = insn; line != null; line = line.getPrevious()) {
            if (line instanceof LineNumberNode) {
                return String.format("the write to %s at line %d", state, ((LineNumberNode) line).line);
            }
        }
        return "the write to " + state;
    }

}
