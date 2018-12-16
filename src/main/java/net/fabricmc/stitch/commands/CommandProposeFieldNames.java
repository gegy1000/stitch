/*
 * Copyright (c) 2016, 2017, 2018 Adrian Siekierka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.stitch.commands;

import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.util.ClassProposalSet;
import net.fabricmc.stitch.util.NameProposalIndexer;
import org.objectweb.asm.ClassReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CommandProposeFieldNames extends Command {
    public CommandProposeFieldNames() {
        super("proposeFieldNames");
    }

    @Override
    public String getHelpString() {
        return "<input jar> <input mappings> <output mappings>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count == 3;
    }

    @Override
    public void run(String[] args) throws Exception {
        File file = new File(args[0]);
        Map<String, ClassProposalSet> classProposals = buildProposals(file);

        int proposalCount = classProposals.values().stream()
                .mapToInt(ClassProposalSet::getProposalCount)
                .sum();
        System.err.println("Found " + proposalCount + " interesting names.");

        try (FileInputStream fileIn = new FileInputStream(new File(args[1]))) {
            try (FileOutputStream fileOut = new FileOutputStream(new File(args[2]))) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(fileIn));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fileOut));

                int headerPos = -1;

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] tabSplit = line.split("\t");

                    String classKey = tabSplit[1];
                    if (headerPos < 0) {
                        // first line
                        if (tabSplit.length < 3 || !(classKey.equals("official"))) {
                            throw new RuntimeException("Invalid mapping file!");
                        }

                        for (int i = 2; i < tabSplit.length; i++) {
                            if (tabSplit[i].equals("named")) {
                                headerPos = i;
                                break;
                            }
                        }

                        if (headerPos < 0) {
                            throw new RuntimeException("Could not find 'named' mapping position!");
                        }
                    } else {
                        // second+ line
                        if (tabSplit[0].equals("FIELD")) {
                            String value = tabSplit[headerPos + 2];
                            if (value.startsWith("field_")) {
                                ClassProposalSet proposals = classProposals.get(classKey);

                                if (proposals != null) {
                                    String fieldName = tabSplit[3];
                                    String proposedName = proposals.getProposal(fieldName);
                                    if (proposedName != null) {
                                        tabSplit[headerPos + 2] = proposedName;

                                        StringBuilder builder = new StringBuilder(tabSplit[0]);
                                        for (int i = 1; i < tabSplit.length; i++) {
                                            builder.append('\t');
                                            builder.append(tabSplit[i]);
                                        }
                                        line = builder.toString();
                                    }
                                }
                            }
                        }
                    }

                    if (!line.endsWith("\n")) {
                        line = line + "\n";
                    }

                    writer.write(line);
                }

                reader.close();
                writer.close();
            }
        }
    }

    private Map<String, ClassProposalSet> buildProposals(File file) throws IOException {
        Map<String, ClassProposalSet> classProposals = new HashMap<>();
        NameProposalIndexer proposalIndexer = new NameProposalIndexer();

        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                try (DataInputStream input = new DataInputStream(jar.getInputStream(entry))) {
                    ClassReader reader = new ClassReader(input);
                    String className = reader.getClassName();

                    ClassProposalSet proposalSet = proposalIndexer.buildProposals(className, reader);
                    if (!proposalSet.isEmpty()) {
                        classProposals.put(className, proposalSet);
                    }
                }
            }
        }

        return classProposals;
    }
}
