package assembler;

import cpu.Memory;
import util.Console;
import util.InstructionInformationList;
import util.LabelList;

import java.util.*;

public class Assembler {
    private static Console console;
    public static byte[] assemble(String[] lines) {
        //all the opcodes
        InstructionInformationList iil = OpcodeTable.OPCODES;


        //this takes care of vars and labels
        //a var would be            -> (name, pc, size) -> rom[address] = val
        //a normal label would be   -> (name, pc, 2)
        LabelList labelData = new LabelList();

        //put here so vars could send val to memory
        byte[] program = new byte[Memory.ROM_SIZE];

        //get all labels in advance
        List<String> labelList = new ArrayList<>();
        for(int i = 0 ; i < lines.length; i++){
            //remove comments
            lines[i] = removeComments(lines[i]);
            if(lines[i].trim().isEmpty()) continue;
            int idx = lines[i].indexOf(':');
            if(idx != -1)
                labelList.add(lines[i].substring(0, idx));
        }
        //pass one will go through each instruction and
        // when it sees some label it will note the label and pc
        //then remove the label
        int pc = 0;
        for(int i = 0 ; i < lines.length; i++){
            if(lines[i].trim().isEmpty()) continue;

            //add <label, pc> to 'labels' and remove from line
            //this could also be a variable
            String line = lines[i];
            String lineU = lines[i].toUpperCase();
            if(line.contains(":")){
                int idx = line.indexOf(':');
                //handle variables
                if(lineU.contains("DB ") || lineU.contains("DW ") || lineU.contains("DS ")) {
                    String[] tokens = line.split(",");
                    String[] firstElement = tokens[0].split("\\s+");
                    tokens[0] = firstElement[firstElement.length-1];
                    if (lineU.contains("DB ")) {
                        labelData.put(line.substring(0, idx), pc, 1);
                        for(int t = 0; t < tokens.length; t++) {
                            Integer imm8 = decodeImmediate(tokens[t]);
                            byte[] immS = decodeImmediateString(tokens[t]);
                            if (imm8 != null)
                                program[pc++] = (byte) (imm8 & 0xFF);
                            else if(immS.length != 0){
                                System.arraycopy(immS, 0, program, pc, immS.length);
                                pc += immS.length;
                            }
                            else throw new RuntimeException("No value for variable: " + line.substring(0, idx));
                            console.println(3, tokens[t]);
                        }
                    }
                    if (lineU.contains("DW ")) {
                        for(int t = 0; t < tokens.length; t++) {
                            Integer imm16 = decodeImmediate(tokens[t]);
                            if (imm16 != null) {
                                byte low = (byte) (imm16 & 0xFF);
                                byte high = (byte) (((imm16 & 0xFF00) >> 8) & 0xFF);
                                labelData.put(line.substring(0, idx), pc, 2);
                                program[pc++] = low;
                                program[pc++] = high;
                            } else throw new RuntimeException("No value for variable: " + line.substring(0, idx));
                        }
                    }
                    if (lineU.contains("DS ")) {
                        int n = Integer.parseInt(tokens[tokens.length-1]);
                        labelData.put(line.substring(0, idx), pc, n);
                        pc += n;
                    }
                    lines[i] = "";
                    continue;
                }
                labelData.put(line.substring(0, idx), pc, 2);
                lines[i] = line.substring(idx+1).trim();
            }
            //skip if after label removal(or not) is empty
            if(lines[i].isEmpty()) continue;

            //handle org
            if(lines[i].toUpperCase().startsWith("ORG")){
                Integer address = decodeImmediate(lines[i].split("\\s+")[1]);
                if(address != null){
                    pc = address & 0xFFFF;
                }
                continue;
            }

            //check if any token is an immediate or label and collect tokens that are neither
            String[] tokens = lines[i].split("\\s+");
            //imm or label
            String mnemonic = "", iorl = "";
            for(String token : tokens){
                Integer imm = decodeImmediate(token);
                //if imm is null it is not a number in any form(hex,bin,oct,dec)
                //imm==null means it's a word,
                //labels not containing the word means it's a part of opcode
                //vars not containing the word means it's a part of opcode
                if(imm == null && !labelList.contains(token)){
                    mnemonic += token + " ";
                } else iorl += (imm==null?token:imm);
                //guaranteed to have at most one immediate per instruction
            }
            mnemonic = mnemonic.trim();

            //set line to opcode
            lines[i] = iil.geto(mnemonic) + " " + iorl;
            //get and add size to pc
            int size = iil.gets(mnemonic);

            if(size == -1){
                throw new RuntimeException("Unknown opcode: " + Arrays.toString(tokens) + " at line: "+(i+1));
            }
            pc += size;
        }

        console.println(3, labelData.toString());
        //now we know where labels are
        //in pass 2 we replace labels with imm16
        //all lines are in form '(opcode) imm8/16/label/var'
        //if imm8 or var then size is 2
        //if imm16 or label or var it is size 3
        pc=0;
        for(int i = 0 ; i < lines.length; i++){
            String instruction = lines[i];
            if(instruction.trim().isEmpty()) continue;

            if(instruction.toUpperCase().startsWith("ORG")){
                Integer address = decodeImmediate(instruction.split("\\s+")[1]);
                if(address != null){
                    pc = address & 0xFFFF;
                }
                continue;
            }


            String[] tokens = instruction.split("\\s+");
            int opcode = Integer.parseInt(tokens[0]) & 0xFF;
            program[pc++] = (byte) (opcode & 0xFF);
            int size = iil.gets(opcode);
            //size = 1 then there is no imm or label
            if(size == 2){
                //imm8
                Integer imm8 = decodeImmediate(tokens[1]);
                if(imm8 != null){
                    program[pc++] = (byte) (imm8 & 0xFF);
                } else{
                    int address = labelData.geta(tokens[1]);
                    program[pc++] = program[address & 0xFFFF];
                }

            }
            if(size == 3){
                //imm16
                try {
                    if (isNumber(tokens[1])) {
                        int imm16 = Integer.parseInt(tokens[1]) & 0xFFFF;
                        byte low = (byte) (imm16 & 0xFF);
                        byte high = (byte) ((imm16 >> 8) & 0xFF);
                        program[pc++] = (byte) (low & 0xFF);
                        program[pc++] = (byte) (high & 0xFF);
                    }
                    //label/var
                    else if (labelList.contains(tokens[1])) {
                        int address = labelData.geta(tokens[1]) & 0xFFFF;
                        byte low = (byte) (address & 0xFF);
                        byte high = (byte) ((address >> 8) & 0xFF);
                        program[pc++] = low;
                        program[pc++] = high;

                    }
                } catch(ArrayIndexOutOfBoundsException e){
                    System.err.printf("Incorrect number of args on line %d.\n", i+1);
                }
            }
        }
        return program;
    }

    private static boolean isNumber(String word){
        return word.charAt(0) >= '0' && word.charAt(0) <= '9';
    }

    private static String removeComments(String s){
        int commentIdx = s.indexOf(';');
        if(commentIdx == -1) return s.trim();
        return s.substring(0, commentIdx).trim();
    }

    private static Integer decodeImmediate(String imm){
        imm = imm.trim();
        String immU = imm.toUpperCase();
        if(imm.isEmpty()) return null;
        //hex
        if(immU.startsWith("0X") || immU.endsWith("H")){
            if(immU.startsWith("0X")){
                int res = 0;
                int power = 0;
                for(int i = imm.length()-1 ; i >= 2; i--, power++){
                    if(!isCharHex(imm.charAt(i))) return null;
                    res += (int) Math.pow(16, power) * ctoh(imm.charAt(i));
                }
                return res;
            }
            if(immU.endsWith("H") && immU.length() > 1){
                int res = 0;
                int power = 0;
                for(int i = imm.length()-2 ; i >= 0; i--, power++){
                    if(!isCharHex(imm.charAt(i))) return null;
                    res += (int) Math.pow(16, power) * ctoh(imm.charAt(i));
                }
                return res;
            }
        }
        //octal
        if((immU.endsWith("O") || immU.endsWith("Q")) && imm.length() > 1){
            int res = 0;
            int power = 0;
            for(int i = imm.length()-2 ; i >= 0; i--, power++){
                if(!isCharOctal(imm.charAt(i))) return null;
                res += (int) Math.pow(8, power) * (imm.charAt(i)-'0');
            }
            return res;
        }
        //binary
        if((immU.endsWith("B") && imm.length() > 1) || (immU.startsWith("0B") && imm.length() > 2)){
            if(immU.endsWith("B")) {
                int res = 0;
                int power = 0;
                for (int i = imm.length() - 2; i >= 0; i--, power++) {
                    if (!isCharBinary(imm.charAt(i))) return null;
                    res += (int) Math.pow(2, power) * (imm.charAt(i) - '0');
                }
                return res;
            }
            if(immU.startsWith("0B")) {
                int res = 0;
                int power = 0;
                for (int i = imm.length() - 1; i >= 2; i--, power++) {
                    if (!isCharBinary(imm.charAt(i))) return null;
                    res += (int) Math.pow(2, power) * (imm.charAt(i) - '0');
                }
                return res;
            }
        }
        //char
        if(imm.length() == 3 && imm.charAt(0) == '\'' && imm.charAt(2) == '\''){
            return (int) imm.charAt(1);
        }
        if(!imm.matches("-?[0-9]+")) return null;
        return Integer.parseInt(imm);
    }

    public static byte[] decodeImmediateString(String imm){
        if(imm.charAt(0) == '"' && imm.charAt(imm.length()-1) == '"'){
            byte[] immS = new byte[imm.length()-2];
            for(int i = 0 ; i < immS.length; i++){
                immS[i] = (byte)imm.charAt(i+1);
            }
            return immS;
        }
        return new byte[0];
    }
    //char to hex
    private static int ctoh(char c){
        c = Character.toUpperCase(c);
        if(!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'F')){
            throw new RuntimeException("Not hexadecimal: " + c);
        }
        return switch(c){
            case 'A'-> 10;
            case 'B'-> 11;
            case 'C'-> 12;
            case 'D'-> 13;
            case 'E'-> 14;
            case 'F'-> 15;
            default -> c-'0';
        };
    }
    private static boolean isCharHex(char c){
        c = Character.toUpperCase(c);
        return Character.isDigit(c) || (c >= 'A' && c <= 'F');
    }
    private static boolean isCharOctal(char c){
        return c >= '0' && c <= '8';
    }
    private static boolean isCharBinary(char c){
        return c == '0' || c == '1';
    }

    public static void attachConsole(Console _console){
        console = _console;
    }

}
