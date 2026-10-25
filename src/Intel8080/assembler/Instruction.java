package Intel8080.assembler;

/** Legacy parsed instruction: mnemonic and operand strings. */
public class Instruction {
    private String mnemonic;
    private String[] args;
    /** Creates an instruction from its mnemonic and operands. */
    public Instruction(String mnemonic, String[] args){
        this.mnemonic = mnemonic;
        this.args = args;
    }
    /** Returns the mnemonic. */
    public String getMnemonic(){
        return mnemonic;
    }
    /** Returns the operand strings. */
    public String[] getArgs(){
        return args;
    }
}
