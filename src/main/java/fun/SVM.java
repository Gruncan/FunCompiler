package fun;

import java.io.PrintStream;
import java.util.Scanner;

/**
 * Representation and interpretation of FunVM code.
 * Based on a previous version developed by
 * David Watt and Simon Gay (University of Glasgow).
 */
public class SVM {

    // Each SVM object is a simple virtual machine.
    // This comprises a code store, a data store, and
    // registers pc (program counter), sp (stack pointer),
    // fp (frame pointer), and status (initially RUNNING).

    // The data store contains a stack of words. Register sp
    // points to the first free word above the stack top.

    // The code store contains byte-codes.
    // Each instruction occupies 1-3 bytes, in which the
    // first byte contains the opcode.
    // Register pc points to the first byte of the next
    // instruction to be executed.
    // The instruction set is as follows:
    //
    // Opcode Bytes Mnemonic	Behaviour
    //    0    1+2  LOADG d    w <- word at global address d;
    //                         push w.
    //    1    1+2  STOREG d   pop w;
    //                         word at global address d <- w.
    //    2    1+2  LOADL d    w <- word at local address d;
    //                         push w.
    //    3    1+2  STOREL d   pop w;
    //                         word at local address d <- w.
    //    4    1+2  LOADC w    push w.
    //    6    1    ADD        pop w2; pop w1; push (w1+w2).
    //    7    1    SUB        pop w2; pop w1; push (w1-w2).
    //    8    1    MUL        pop w2; pop w1; push (w1*w2).
    //    9    1    DIV        pop w2; pop w1; push (w1/w2).
    //   10    1    CMPEQ      pop w2; pop w1;
    //                         push (if w1=w2 then 1 else 0).
    //   12    1    CMPLT      pop w2; pop w1;
    //                         push (if w1<w2 then 1 else 0).
    //   13    1    CMPGT      pop w2; pop w1;
    //                         push (if w1>w2 then 1 else 0).
    //   14    1    INV        pop w;
    //                         push (if w==0 then 1 else 0).
    //   15    1    INC        pop w; push w+1.
    //   16    1    HALT       status <- HALTED.
    //   17    1+2  JUMP c     pc <- c.
    //   18    1+2  JUMPF c    pop w; if w=0 then pc <- c.
    //   19    1+2  JUMPT c    pop w; if w!=0 then pc <- c.
    //   20    1+2  CALL c     push a new frame initially
    //                         containing only
    //                         dynamic link <- fp and
    //                         return address <- pc;
    //                         fp <- base address of frame;
    //                         pc <- c.
    //   21    1+1  RETURN r   pop the result (r words);
    //                         pop topmost frame;
    //                         push the result (r words);
    //                         fp <- dynamic link;
    //                         pc <- return address.
    //   22    1+1  COPYARG s  swap arguments (s words) into
    //                         the topmost frame, just above
    //                         the return address.

    public static final byte        // opcodes
            LOADG = 0, STOREG = 1,
            LOADL = 2, STOREL = 3,
            LOADC = 4,
            ADD = 6, SUB = 7,
            MUL = 8, DIV = 9,
            CMPEQ = 10,
            CMPLT = 12, CMPGT = 13,
            INV = 14, INC = 15,
            HALT = 16, JUMP = 17,
            JUMPF = 18, JUMPT = 19,
            CALL = 20, RETURN = 21,
            COPYARG = 22;
    public static final byte        // status codes
            RUNNING = 0,
            HALTED = 1,
            FAILED = 2;
    public static final int         // offsets of IO routines
            READ_OFF_SET = 32766,
            WRITE_OFF_SET = 32767,
            IO_BASE = 32766;
    private static final String[] MNEMONIC = {
            "LOADG   ", "STOREG  ",
            "LOADL   ", "STOREL  ",
            "LOADC   ", "???     ",
            "ADD     ", "SUB     ",
            "MUL     ", "DIV     ",
            "CMPEQ   ", "???     ",
            "CMPLT   ", "CMPGT   ",
            "INV     ", "INC     ",
            "HALT    ", "JUMP    ",
            "JUMPF   ", "JUMPT   ",
            "CALL    ", "RETURN  ",
            "COPYARG "};
    private static final int[] BYTES = {
            3, 3,
            3, 3,
            3, 1,
            1, 1,
            1, 1,
            1, 1,
            1, 1,
            1, 1,
            1, 3,
            3, 3,
            3, 2,
            2};


    // MACHINE STATE
    private static final Scanner in = new Scanner(System.in);
    private static final PrintStream out = System.out;
    protected byte[] code;     // code store
    protected int cl;          // code limit
    protected int pc;          // program counter
    protected int[] data;      // data store (stack)
    protected int sp;          // stack pointer


    // CONSTRUCTOR
    protected int fp;          // frame pointer


    // CODE INTERPRETATION
    protected byte status;

    public SVM() {
        this.code = new byte[32768];
        this.cl = 0;
    }

    public void interpret(boolean tracing) {
        // Interpret the program starting at offset 0
        // in the code store.
        // If tracing is true, print each instruction
        // as it is executed.
        this.data = new int[32768];
        this.pc = 0;
        this.sp = 0;
        this.fp = 0;
        this.status = RUNNING;
        do {
            if (tracing) out.println(showInstruction(pc));
            byte opcode = this.code[this.pc++];
            switch (opcode) {
                case LOADG -> {
                    // addr of global variable
                    int d = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    this.data[this.sp++] = this.data[d];
                }
                case STOREG -> {
                    // addr of global variable
                    int d = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    this.data[d] = this.data[--this.sp];
                }
                case LOADL -> {
                    // addr of local variable
                    int d = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    this.data[this.sp++] = this.data[fp + d];
                }
                case STOREL -> {
                    // addr of local variable
                    int d = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    this.data[fp + d] = this.data[--this.sp];
                }
                case LOADC -> {
                    // addr of local variable
                    int w = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    this.data[this.sp++] = w;
                }
                case ADD -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = w1 + w2;
                }
                case SUB -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = w1 - w2;
                }
                case MUL -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = w1 * w2;
                }
                case DIV -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = w1 / w2;
                }
                case CMPEQ -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = (w1 == w2 ? 1 : 0);
                }
                case CMPLT -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = (w1 < w2 ? 1 : 0);
                }
                case CMPGT -> {
                    int w2 = this.data[--this.sp];
                    int w1 = this.data[--this.sp];
                    this.data[this.sp++] = (w1 > w2 ? 1 : 0);
                }
                case INV -> {
                    int w = this.data[--this.sp];
                    this.data[this.sp++] = (w == 0 ? 1 : 0);
                }
                case INC -> {
                    int w = this.data[--this.sp];
                    this.data[this.sp++] = w + 1;
                }
                case HALT -> {
                    this.status = HALTED;
                }
                case JUMP -> {
                    // target of jump
                    this.pc = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                }
                case JUMPF -> {
                    // target of jump
                    int c = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    int w = this.data[--this.sp];

                    if (w == 0) this.pc = c;
                }
                case JUMPT -> {
                    // target of jump
                    int c = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);
                    int w = this.data[--this.sp];

                    if (w != 0) this.pc = c;
                }
                case CALL -> {
                    // address of callee
                    int c = this.code[this.pc++] << 8 | (this.code[this.pc++] & 0xFF);

                    if (c >= IO_BASE) {
                        callIO(c);
                        break;
                    }
                    this.data[this.sp++] = this.fp;  // dyn link
                    this.data[this.sp++] = this.pc;  // return addr
                    this.fp = this.sp - 2;
                    this.pc = c;
                }
                case RETURN -> {
                    int r = this.code[this.pc++];  // result size
                    int dl = this.data[this.fp];   // dyn link
                    int ra = this.data[this.fp + 1]; // return addr
                    // Shift result down to top of
                    // caller's frame:
                    for (int i = 0; i < r; i++) this.data[this.fp + i] = this.data[this.sp - r + i];

                    this.sp = this.fp + r;
                    this.fp = dl;
                    this.pc = ra;
                }
                case COPYARG -> {
                    int s = this.code[this.pc++];  // args size
                    int dl = this.data[this.fp];   // dyn link
                    int ra = this.data[this.fp + 1]; // return addr
                    // Shift arguments up by 2 words,
                    // to make room for link this.data:
                    for (int i = 0; i < s; i++) this.data[this.fp - i + 1] = this.data[this.fp - i - 1];

                    // Move link this.data under arguments:
                    this.fp -= s;
                    this.data[this.fp] = dl;
                    this.data[this.fp + 1] = ra;
                }
                default -> {
                    out.println("Illegal instruction" + opcode);
                    this.status = FAILED;
                }
            }
        } while (this.status == RUNNING);
    }

    private void callIO(int c) {
        // Execute a call to an IO routine.
        switch (c) {
            case READ_OFF_SET -> {
                out.print("? ");
                int w = in.nextInt();
                this.data[this.sp++] = w;
            }
            case WRITE_OFF_SET -> {
                int w = this.data[--this.sp];
                out.println(w);
            }
        }
    }


    // CODE DISPLAY
    public String showCode() {
        // Return a textual representation of all the code.
        StringBuilder assembly = new StringBuilder();
        for (int c = 0; c < this.cl; ) {
            assembly.append(showInstruction(c)).append("\n");
            c += BYTES[this.code[c]];
        }
        return assembly.toString();
    }

    private String showInstruction(int c) {
        // Return a textual representation of the instruction
        // at offset c in the code store.
        byte opcode = this.code[c++];
        String line = String.format("%6d: %s", c - 1, MNEMONIC[opcode]);
        switch (BYTES[opcode]) {
            case 2 -> {
                byte operand = this.code[c++];
                line += operand;
            }
            case 3 -> {
                int operand = this.code[c++] << 8 | (this.code[c++] & 0xFF);
                line += operand;
            }
        }
        return line;
    }


    // STACK DISPLAY
    public String showStack() {
        // Return a textual representation of the stack contents.
        StringBuilder show = new StringBuilder();
        int dl = this.fp;
        for (int a = this.sp - 1; a >= 0; a--) {
            show.append(String.format("%6d: %6d\n", a, this.data[a]));

            if (a == dl) {
                show.append("        ------\n");
                dl = this.data[a];
            }
        }
        return show.toString();
    }


    // CODE EMISSION
    public void emit1(byte opcode) {
        // Add a 1 byte instruction to the code.
        this.code[this.cl++] = opcode;
    }

    public void emit11(byte opcode,
                       int operand) {
        // Add a 1+1 byte instruction to the code.
        this.code[this.cl++] = opcode;
        this.code[this.cl++] = (byte) operand;
    }

    public void emit12(byte opcode,
                       int operand) {
        // Add a 1+2 byte instruction to the code.
        this.code[this.cl++] = opcode;
        this.code[this.cl++] = (byte) (operand >> 8);
        this.code[this.cl++] = (byte) (operand & 0xFF);
    }

    public void patch12(int addr, int operand) {
        // Patch an operand into a 1+2 byte instruction.
        this.code[addr + 1] = (byte) (operand >> 8);
        this.code[addr + 2] = (byte) (operand & 0xFF);
    }

    public int currentOffset() {
        // Return the offset of the next instruction to be added.
        return this.cl;
    }

}