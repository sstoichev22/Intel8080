package assembler;

import util.InstructionInformationList;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class Assembler {

    private static final Set<String> REGISTERS = Set.of("A","B","C","D","E","H","L","M");

    public static byte[] assemble(List<String> lines) {
        InstructionInformationList iil = OpcodeTable.OPCODES;
        Map<String, Integer> labels = new HashMap<>();
        int pc = 0;
        //turn lines to possible labels
        for(String instruction : lines){
            instruction = removeComments(instruction);
            if(instruction.isEmpty()) continue;
            if(instruction.endsWith(":"))
                labels.put(instruction, pc);
            String[] tokens = instruction.split("\\s+");
            StringBuilder i = new StringBuilder();
            for(String token : tokens){
                Integer imm = decodeImmediate(token);
                if(imm == null){
                    i.append(token + " ");
                }
            }
            int opcode = iil.geto(i.toString().trim());
            if(opcode == -1)
                throw new RuntimeException("Not an opcode. [" + i.toString() + "]");
            pc += iil.gets(opcode);
        }

        //now with label addresses we can make instructions
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        for(String instruction : lines){
            instruction = removeComments(instruction);
            if(instruction.isEmpty()) continue;
            //we dont care about labels anymore
            if(labels.containsKey(instruction)) continue;
            for(Map.Entry<String, Integer> labelpair : labels.entrySet()){
                String label = labelpair.getKey();
                String hexAddress = Integer.toHexString(labelpair.getValue() & 0xFFFF);
                instruction = instruction.replaceAll(label, hexAddress);
            }
            String[] tokens = instruction.split("\\s+");
            StringBuilder i = new StringBuilder();
            Integer imm = null;
            for(String token : tokens){
                imm = decodeImmediate(token);
                if(imm == null){
                    i.append(token + " ");
                }
            }
            int opcode = iil.geto(i.toString().trim());
            int size = iil.gets(opcode);
            program.write(opcode & 0xFF);

            assert imm != null;
            // cant because we set the size of each instruction and we expect a value
            if(size == 2){
                program.write(imm & 0xFF);
            }
            if(size == 3){
                program.write(imm & 0xFF);
                program.write((imm >> 8) & 0xFF);
            }
        }
        return program.toByteArray();
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
        if((imm.startsWith("0X") || imm.endsWith("H")) && Character.isDigit(imm.charAt(0))){
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
        if(!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'F')) return -1;
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
