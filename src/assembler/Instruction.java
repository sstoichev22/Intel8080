package assembler;

public class Instruction {
    private String mnemonic;
    private String[] args;
    public Instruction(String mnemonic, String[] args){
        this.mnemonic = mnemonic;
        this.args = args;
    }
    public String getMnemonic(){
        return mnemonic;
    }
    public String[] getArgs(){
        return args;
    }
}
