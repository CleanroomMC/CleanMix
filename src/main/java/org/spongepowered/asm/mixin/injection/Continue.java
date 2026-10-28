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
package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skips to the next iteration of a loop around the injection point when the
 * handler returns <tt>true</tt>, as if a <tt>continue</tt> statement was
 * written just before the selected instruction.
 * 
 * <pre>{@code
 * @Continue(method = "tickEntities", at = @At(value = "INVOKE", target = "tick()V"))
 * private boolean skipFrozen() {
 *     return this.frozen;
 * }
 * }</pre>
 * 
 * <p>The handler returns <tt>boolean</tt> and can take the target method's
 * arguments, the same way an {@link Inject} handler does. It takes no
 * <tt>CallbackInfo</tt>, so a hot loop does not allocate one on every
 * iteration.</p>
 *
 * <h3>Loop scenarios</h3>
 *
 * <table width="100%" class="infoTable">
 *   <tr>
 *     <th width="40%">Loop</th>
 *     <th>What to select</th>
 *   </tr>
 *   <tr>
 *     <td>A counted loop such as {@code for (int i = 0; i < n; i++)} or
 *       one stepping with <tt>i += step</tt>, and for-each loops</td>
 *     <td>Only <tt>at</tt>. The jump runs the counter.</td>
 *   </tr>
 *   <tr>
 *     <td><tt>while (cond)</tt> with no counter at the end of its body</td>
 *     <td>Only <tt>at</tt>. The jump goes to the condition.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code while (i < n) { ...; i++; }}</td>
 *     <td>Only <tt>at</tt>. The jump runs <tt>i++</tt>, where Java's
 *       <tt>continue</tt> would skip it and loop forever.</td>
 *   </tr>
 *   <tr>
 *     <td>An update through a field or call, such as <tt>node = node.next</tt>
 *       or <tt>this.k++</tt></td>
 *     <td>{@link #to} matching a field or call in the update.</td>
 *   </tr>
 *   <tr>
 *     <td>A loop tested at the end, such as <tt>do-while</tt> or
 *       <tt>while (true)</tt> ending in <tt>if (done) break;</tt></td>
 *     <td>{@link #to} matching an instruction in that test. When the test
 *       joins conditions with {@code &&} or {@code ||}, match its
 *       first <tt>JUMP</tt>. The jump runs the test.</td>
 *   </tr>
 *   <tr>
 *     <td>A statement after <tt>at</tt> that advances the loop, such as
 *       <tt>p = p.up()</tt> in the middle of the body</td>
 *     <td>{@link #to} matching that statement, or the statement after it when
 *       it only uses locals. The injector names the statement when it
 *       fails.</td>
 *   </tr>
 *   <tr>
 *     <td><tt>at</tt> inside the statement that advances the loop, as in
 *       <tt>while (s.endsWith(" ")) s = s.substring(1);</tt></td>
 *     <td>Another <tt>at</tt>. Continuing from there would loop forever in
 *       plain Java too.</td>
 *   </tr>
 *   <tr>
 *     <td>An outer loop, like <tt>continue outer;</tt></td>
 *     <td>{@link #loop} matching one instruction in the outer loop, or
 *       {@link #to} matching a field or call in its update.</td>
 *   </tr>
 * </table>
 *
 * <h3>Where the next iteration starts</h3>
 *
 * <p>The loop is found from its backward jumps the same way {@link Break}
 * finds it. Finding where <tt>continue</tt> lands is harder. Java's
 * <tt>continue</tt> runs a <tt>for</tt> loop's update but skips the end of a
 * <tt>while</tt> loop's body, and {@code for (; i < n; i++) body} compiles
 * to the same bytecode as {@code while (i < n) { body; i++; }}. Nothing
 * jumps to the update, so no label marks it. Minecraft's classes also lack the
 * local variable table, and line numbers cannot split a loop written on one
 * line.</p>
 *
 * <p>So the injector picks a landing that works for both versions. Without
 * {@link #to}, the jump lands on the counters at the end of the loop, or on the
 * backward jump when there are none. A counter is a statement that only uses
 * locals, writes a variable the condition reads, and runs on every path to the
 * next iteration, such as <tt>i++</tt> or <tt>i += step</tt>. No {@link At}
 * can match a counter, so the injector finds counters itself. With
 * {@link #to}, the jump lands where the statement holding the matched
 * instruction starts, which is the nearest point before it where the stack is
 * back at the loop's base height. Counters right before that statement run
 * too.</p>
 *
 * <p>A loop tested at the end always needs {@link #to}. The bytecode of
 * <tt>if (x) break; } while (c);</tt> matches <tt>} while (!x &amp;&amp; c);</tt>,
 * so it cannot show where the test starts.</p>
 *
 * <p>Before injecting, the injector follows every path from the injection
 * point to the next iteration. If every path writes a variable or field the
 * condition reads, skipping those writes could loop forever, so the injector
 * fails and names the write and its line. A write that only some paths make,
 * such as <tt>found = true</tt> inside an <tt>if</tt>, does not count. The
 * injector also drops values the expression at the injection point already
 * pushed, verifies the method, and refuses to leave a <tt>finally</tt> or
 * <tt>synchronized</tt> block, the same way {@link Break} does. It does not
 * apply when the jump would skip code another injector added, see
 * {@link #order}.</p>
 *
 * <h3>Coverage</h3>
 *
 * <p>A static survey ran these rules over every loop in the Minecraft 1.12.2
 * client and in Forge 14.23.5.2864's own classes, with <tt>at</tt> on the first
 * statement of each loop body. Each loop in the update row has an instruction
 * {@link #to} can match.</p>
 *
 * <table width="100%" class="infoTable">
 *   <tr>
 *     <th width="60%">Landing</th>
 *     <th>Vanilla</th>
 *     <th>Forge</th>
 *   </tr>
 *   <tr><td>Automatic, on the backward jump</td><td>1107</td><td>593</td></tr>
 *   <tr><td>Automatic, on counters</td><td>2337</td><td>452</td></tr>
 *   <tr><td>{@link #to} on an update or a later statement</td><td>74</td>
 *     <td>8</td></tr>
 *   <tr><td>{@link #to} on the test of a loop tested at the end</td><td>28</td>
 *     <td>10</td></tr>
 *   <tr><td>Another <tt>at</tt>, since the first statement advances the
 *     loop</td><td>131</td><td>13</td></tr>
 *   <tr><td>None, the body is empty</td><td>11</td><td>1</td></tr>
 *   <tr><td>Total</td><td>3688</td><td>1077</td></tr>
 * </table>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Continue {

    /**
     * See {@link Inject#method()}.
     *
     * @return target method(s) for this injector
     */
    public String[] method() default {};

    /**
     * See {@link Inject#target()}.
     *
     * @return target method(s) for this injector as descriptors
     */
    public Desc[] target() default {};

    /**
     * See {@link Inject#slice()}.
     *
     * @return slices
     */
    public Slice[] slice() default {};

    /**
     * Instructions to skip to the next iteration before, see {@link Inject#at()}.
     *
     * @return injection points
     */
    public At[] at();

    /**
     * Selects the loop to continue by matching one instruction inside it, such
     * as a call in its condition. The innermost loop around that instruction is
     * used and it must contain the injection point. Leave empty to use the
     * innermost loop around the injection point.
     *
     * @return loop selector matching exactly one instruction
     */
    public At[] loop() default {};

    /**
     * Selects where the next iteration starts by matching one instruction after
     * the injection point, such as a field or call in the loop's update, or a
     * <tt>JUMP</tt> in a <tt>do-while</tt> condition. The jump lands at the
     * start of the statement holding that instruction, along with any counters
     * right before it. Counters cannot be selected, so select the statement
     * after them. Selecting a statement inside an <tt>if</tt>, or the second
     * half of a condition joined with {@code &&} or {@code ||},
     * fails, so select the first <tt>JUMP</tt> of its test. Leave empty to find
     * the next iteration automatically.
     * 
     * <pre>{@code
     * @Continue(method = "tickChain", at = @At(value = "INVOKE", target = "tick()V"),
     *         to = @At(value = "FIELD", target = "Lcom/example/Node;next:Lcom/example/Node;"))
     * }</pre>
     *
     * @return where the next iteration starts, matching exactly one instruction
     */
    public At[] to() default {};

    /**
     * See {@link Inject#remap()}.
     *
     * @return true to remap the target selectors
     */
    public boolean remap() default true;

    /**
     * See {@link Inject#require()}.
     *
     * @return minimum required number of injections
     */
    public int require() default -1;

    /**
     * See {@link Inject#expect()}.
     *
     * @return expected number of injections
     */
    public int expect() default 1;

    /**
     * See {@link Inject#allow()}.
     *
     * @return maximum allowed number of injections
     */
    public int allow() default -1;

    /**
     * See {@link Inject#constraints()}.
     *
     * @return constraints for this annotation
     */
    public String constraints() default "";

    /**
     * See {@link Inject#order()}. When the jump would skip code that another
     * injector added before this one applied, this injector does not apply. It
     * logs a warning instead, and {@link #require} turns that into an error. A
     * lower order applies this injector first, so code that other injectors add
     * later is skipped without a warning. {@link Inject}, {@link ModifyArg},
     * {@link ModifyVariable} and this injector default to 1000, while
     * {@link Redirect} and {@link ModifyConstant} always apply later.
     *
     * @return the application order for this injector
     */
    public int order() default 1000;

}
