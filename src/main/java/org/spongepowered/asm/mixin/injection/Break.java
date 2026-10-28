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
 * Leaves a loop around the injection point when the handler returns
 * <tt>true</tt>, as if a <tt>break</tt> statement was written just before the
 * selected instruction.
 * 
 * <pre>{@code
 * @Break(method = "tick", at = @At(value = "INVOKE", target = "update()V"))
 * private boolean stopTicking() {
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
 *     <td>A loop with one way out, including <tt>do-while</tt> and
 *       <tt>while (true)</tt> ending in <tt>if (done) break;</tt></td>
 *     <td>Only <tt>at</tt>.</td>
 *   </tr>
 *   <tr>
 *     <td>An outer loop, like <tt>break outer;</tt></td>
 *     <td>{@link #loop} matching one instruction in the outer loop, such as
 *       its iterator's <tt>hasNext()</tt>.</td>
 *   </tr>
 *   <tr>
 *     <td>A loop with several ways out, such as labelled <tt>break</tt> or
 *       <tt>continue</tt> statements to an outer loop</td>
 *     <td>{@link #loop} matching the jump out of the loop to take, such as an
 *       existing <tt>break</tt>. Without it, the jump takes the exit of the
 *       loop's first test.</td>
 *   </tr>
 *   <tr>
 *     <td>A loop that only leaves through <tt>return</tt> or
 *       <tt>throw</tt></td>
 *     <td>Nothing, since there is no exit to jump to.</td>
 *   </tr>
 * </table>
 *
 * <h3>How the loop and its exit are found</h3>
 *
 * <p>Bytecode has no loop instructions and javac erases statement labels. A
 * loop is a backward jump to its first instruction, its header, and javac and
 * ecj lay each loop out in one block from the header to its last backward
 * jump. The loop used is the innermost block around the injection point, or
 * around the instruction {@link #loop} matches.</p>
 *
 * <p>The jump goes where the loop's own exit jumps go. The instruction after
 * the loop's block is often the wrong place, because javac sends jumps
 * straight to their final target. A <tt>while</tt> loop at the end of an
 * <tt>if</tt> branch exits past the <tt>else</tt> branch, and a loop at the end
 * of an outer loop's body exits straight to the outer loop's next iteration.
 * When the loop has several exits, the injector takes the one its first test
 * jumps to, which is the condition in a <tt>for</tt> or <tt>while</tt> loop.
 * When that test stays inside the loop, a loop that ends in a test, such as
 * <tt>do-while</tt>, falls out of its last test. When {@link #loop} matches a
 * jump out of the loop, the injector uses that jump's target directly.</p>
 *
 * <p>The handler can run partway through an expression, so the jump first
 * drops the values the expression already pushed. The injector then runs the
 * bytecode verifier over the method. It fails when a local read after the loop
 * is not yet assigned where the handler runs, and when the jump would leave a
 * <tt>finally</tt> or <tt>synchronized</tt> block without running it. It does
 * not apply when the jump would skip code another injector added, see
 * {@link #order}.</p>
 *
 * <h3>Coverage</h3>
 *
 * <p>A static survey ran these rules over every loop in the Minecraft 1.12.2
 * client and in Forge 14.23.5.2864's own classes. None of them needed
 * {@link #loop} to pick an exit.</p>
 *
 * <table width="100%" class="infoTable">
 *   <tr>
 *     <th width="60%">Exit used</th>
 *     <th>Vanilla</th>
 *     <th>Forge</th>
 *   </tr>
 *   <tr><td>The only exit</td><td>3651</td><td>1073</td></tr>
 *   <tr><td>The first test's exit, out of several</td><td>30</td><td>3</td></tr>
 *   <tr><td>The last test's fall-through, out of several</td><td>2</td><td>0</td></tr>
 *   <tr><td>None, the loop only leaves through <tt>return</tt> or
 *     <tt>throw</tt></td><td>5</td><td>1</td></tr>
 *   <tr><td>Total</td><td>3688</td><td>1077</td></tr>
 * </table>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Break {

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
     * Instructions to break out of the loop before, see {@link Inject#at()}.
     *
     * @return injection points
     */
    public At[] at();

    /**
     * Selects the loop to leave by matching one instruction inside it, such as
     * a call in its condition. The innermost loop around that instruction is
     * used and it must contain the injection point. When the instruction is a
     * jump out of the loop, such as the condition's exit test or an existing
     * <tt>break</tt>, the handler's jump goes to the same place. Leave empty
     * to use the innermost loop around the injection point.
     *
     * @return loop selector matching exactly one instruction
     */
    public At[] loop() default {};

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
