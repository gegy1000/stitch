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

package net.fabricmc.stitch.enigma;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.analysis.ParsedJar;
import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.mapping.entry.Entry;
import net.fabricmc.stitch.util.ClassProposalSet;
import net.fabricmc.stitch.util.NameProposalIndexer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class StitchEnigmaPlugin implements EnigmaPlugin {
    private final Map<String, ClassProposalSet> classProposals = new HashMap<>();

    @Override
    public void indexJar(ParsedJar jar, JarIndex index) {
        NameProposalIndexer proposalIndexer = new NameProposalIndexer();
        jar.visitReader(reader -> {
            String className = reader.getClassName();
            ClassProposalSet proposalSet = proposalIndexer.buildProposals(className, reader);
            if (!proposalSet.isEmpty()) {
                this.classProposals.put(className, proposalSet);
            }
        });
    }

    @Nullable
    @Override
    public String proposeName(Entry obfEntry, Entry deobfEntry) {
        ClassProposalSet classProposals = this.classProposals.get(obfEntry.getClassName());
        if (classProposals != null) {
            return classProposals.getProposal(obfEntry.getName());
        }
        return null;
    }
}
