package assembler;

import util.InstructionInformationList;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class Assembler {

    private static final Set<String> REGISTERS = Set.of("A","B","C","D","E","H","L","M");

    public static byte[] assemble(List<String> lines) {
        InstructionInformationList iil = new InstructionInformationList();
        Map<String, Integer> labels = new HashMap<>();
        List<Instruction> instructions = new ArrayList<>();
        int pc = 0;
        //turn lines to possible labels
        for(String instruction : lines){
            instruction = removeComments(instruction);
            if(instruction.isEmpty()) continue;
            if(instruction.endsWith(":"))
                labels.put(instruction, pc);
            String[] tokens = instruction.replaceAll(",", " ").split("\\s+");
            StringBuilder i = new StringBuilder();
            for(String token : tokens){
                Integer imm = null;
                try{
                    imm = Integer.decode(token);
                } catch (Exception ignored){}
                if(imm == null){
                    i.append(token);
                }
            }
            int opcode = iil.geto(i.toString());
            if(opcode == -1)
                throw new RuntimeException("Not an opcode.");
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
            String[] tokens = instruction.replaceAll(",", " ").split("\\s+");
            StringBuilder i = new StringBuilder();
            Integer imm = null;
            for(String token : tokens){
                try{
                    imm = Integer.decode(token);
                } catch (Exception e){}
                if(imm == null){
                    i.append(token);
                }
            }
            int opcode = iil.geto(i.toString());
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

}
