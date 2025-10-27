package assembler;

import util.InstructionInformationList;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class Assembler {
    public static byte[] assemble(String[] lines) {
        InstructionInformationList iil = OpcodeTable.OPCODES;
        //label to address
        HashMap<String, Integer> ltoa = new HashMap<>();

        //get all labels in advance
        List<String> labels = new ArrayList<>();
        for(int i = 0 ; i < lines.length; i++){
            int idx = lines[i].indexOf(':');
            if(idx != -1)
                labels.add(lines[i].substring(0, idx));
        }
        //pass one will go through each instruction and
        // when it sees some label it will note the label and pc
        //then remove the label
        int pc = 0;
        for(int i = 0 ; i < lines.length; i++){
            //remove comments
            lines[i] = removeComments(lines[i]);

            //add <label, pc> to 'labels' and remove from line
            if(lines[i].contains(":")){
                int idx = lines[i].indexOf(':');
                ltoa.put(lines[i].substring(0, idx), pc);
                lines[i] = lines[i].substring(idx+1).trim();
            }
            //skip if after label removal(or not) is empty
            if(lines[i].isEmpty()) continue;

            //check if any token is an immediate or label and collect tokens that are neither
            String[] tokens = lines[i].split("\\s+");
            //imm or label
            String mnemonic = "", iorl = "";
            for(String token : tokens){
                Integer imm = decodeImmediate(token);
                //if imm is null it is not a number in any form(hex,bin,oct,dec)
                //imm==null means it's a word,
                // labels not containing the word means it's a part of opcode
                if(imm == null && !labels.contains(token)){
                    mnemonic += token + " ";
                } else iorl += (imm==null?token:imm);
                //guaranteed to have one immediate per instruction
            }
            mnemonic = mnemonic.trim();

            //set line to opcode
            lines[i] = iil.geto(mnemonic) + " " + iorl;
            //get and add size to pc
            int size = iil.gets(mnemonic);
            pc += size;
        }
        //now we know where labels are
        //in pass 2 we replace labels with imm16
        //all lines are in form '(opcode) imm8/16/label
        //if imm8 then size is 2
        //if imm16 or label it is size 3
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        for(String instruction : lines){
            //write opcode
            if(instruction.isEmpty()) continue;
            String[] tokens = instruction.split("\\s+");
            int opcode = Integer.parseInt(tokens[0]) & 0xFF;
            program.write(opcode & 0xFF);
            int size = iil.gets(opcode);
            //size = 1 then there is no imm or label
            if(size == 2){
                //imm8
                byte imm8 = (byte) (Integer.parseInt(tokens[1]) & 0xFF);
                program.write(imm8 & 0xFF);
            }
            if(size == 3){
                //imm16
                if(isNumber(tokens[1])){
                    int imm16 = Integer.parseInt(tokens[1]) & 0xFFFF;
                    byte low = (byte) (imm16 & 0xFF);
                    byte high = (byte) ((imm16 >> 8) & 0xFF);
                    program.write(low);
                    program.write(high);
                }
                //label
                else{
                    int address = ltoa.get(tokens[1]);
                    byte low = (byte) (address & 0xFF);
                    byte high = (byte) ((address >> 8) & 0xFF);
                    program.write(low);
                    program.write(high);
                }
            }
        }
        return program.toByteArray();
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
        imm = imm.toUpperCase().trim();
        if(imm.isEmpty()) return null;
        //hex
        if(imm.startsWith("0X") || imm.endsWith("H")){
            if(imm.startsWith("0X")){
                int res = 0;
                int power = 0;
                for(int i = imm.length()-1 ; i >= 2; i--, power++){
                    if(!Character.isDigit(imm.charAt(i)) && !(imm.charAt(i) >= 'A' && imm.charAt(i) <= 'F')) return null;

                    res += (int) Math.pow(16, power) * ctoh(imm.charAt(i));
                }
                return res;
            }
            if(imm.endsWith("H")){
                int res = 0;
                int power = 0;
                for(int i = imm.length()-2 ; i >= 0; i--, power++){
                    if(!Character.isDigit(imm.charAt(i)) && !(imm.charAt(i) >= 'A' && imm.charAt(i) <= 'F')) return null;
                    res += (int) Math.pow(16, power) * ctoh(imm.charAt(i));
                }
                return res;
            }
        }
        //octal
        if((imm.endsWith("O") || imm.endsWith("Q")) && imm.length() > 1){
            int res = 0;
            int power = 0;
            for(int i = imm.length()-2 ; i >= 0; i--, power++){
                if(!(imm.charAt(i) >= '0' && imm.charAt(i) <= '8')) return null;
                res += (int) Math.pow(8, power) * (imm.charAt(i)-'0');
            }
            return res;
        }
        //binary
        if(imm.endsWith("B") && imm.length() > 1){
            int res = 0;
            int power = 0;
            for(int i = imm.length()-2 ; i >= 0; i--, power++){
                if(!(imm.charAt(i) == '0' || imm.charAt(i) == '1')) return null;
                res += (int) Math.pow(2, power) * (imm.charAt(i)-'0');
            }
            return res;
        }
        //char
        if(imm.length() == 3 && imm.charAt(0) == '\'' && imm.charAt(2) == '\''){
            return (int) imm.charAt(1);
        }
        for(int i = 0 ; i < imm.length(); i++){
            if(!Character.isDigit(imm.charAt(i))) return null;
        }
        return Integer.parseInt(imm);
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

}
