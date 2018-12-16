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

package net.fabricmc.stitch.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class NameProposalIndexer {
	private final Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());

	public ClassProposalSet buildProposals(String owner, ClassReader reader) {
		ClassProposalSet proposalSet = new ClassProposalSet();

		ClinitCollector clinitCollector = new ClinitCollector();
		reader.accept(clinitCollector, ClassReader.SKIP_FRAMES);

		List<MethodNode> methods = clinitCollector.nodes;
		for (MethodNode methodNode : methods) {
			try {
				analyze(proposalSet, owner, methodNode);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return proposalSet;
	}

	private void analyze(ClassProposalSet proposalSet, String owner, MethodNode methodNode) throws Exception {
		Frame<SourceValue>[] frames = analyzer.analyze(owner, methodNode);

		InsnList instructions = methodNode.instructions;
		for (int i = 1; i < instructions.size(); i++) {
			AbstractInsnNode insn1 = instructions.get(i - 1);
			AbstractInsnNode insn2 = instructions.get(i);
			if (!(insn1 instanceof MethodInsnNode && insn2 instanceof FieldInsnNode)) {
				continue;
			}

			MethodInsnNode createInsn = (MethodInsnNode) insn1;
			FieldInsnNode putInsn = (FieldInsnNode) insn2;
			if (!createInsn.owner.equals(owner) || !putInsn.owner.equals(owner)) {
				continue;
			}

			if (putInsn.getOpcode() == Opcodes.PUTSTATIC && (createInsn.getOpcode() == Opcodes.INVOKESTATIC || "<init>".equals(createInsn.name))) {
				String proposal = findProposalLdc(frames[i - 1]);
				if (proposal != null) {
					proposalSet.proposeField(putInsn.name, formatProposal(proposal));
				}
			}
		}
	}

	private String formatProposal(String source) {
		String proposal = source;
		if (proposal.contains(":")) {
			proposal = proposal.substring(proposal.indexOf(':') + 1);
		}

		if (proposal.contains("/")) {
			String sFirst = proposal.substring(0, proposal.indexOf('/'));
			String sLast;
			if (proposal.contains(".")) {
				sLast = proposal.substring(proposal.indexOf('/') + 1, proposal.indexOf('.'));
			} else {
				sLast = proposal.substring(proposal.indexOf('/') + 1);
			}
			if (sFirst.endsWith("s")) {
				sFirst = sFirst.substring(0, sFirst.length() - 1);
			}
			proposal = sLast + "_" + sFirst;
		}

		for (int i = 0; i < proposal.length(); i++) {
			char c = proposal.charAt(i);

			if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && !(c == '_')) {
				proposal = proposal.substring(0, i) + "_" + proposal.substring(i + 1);
			} else if (i > 0 && Character.isUpperCase(proposal.charAt(i)) && Character.isLowerCase(proposal.charAt(i - 1))) {
				proposal = proposal.substring(0, i) + "_" + proposal.substring(i, i + 1).toLowerCase() + proposal.substring(i + 1);
			}
		}

		return proposal.toUpperCase(Locale.ROOT);
	}

	@Nullable
	private String findProposalLdc(Frame<SourceValue> frame) {
		Stream<String> ldcs = collectLdcNodes(frame).stream()
				.filter(node -> node.cst instanceof String)
				.map(node -> (String) node.cst);

		return ldcs.filter(this::isValidProposal).findFirst().orElse(null);
	}

	private boolean isValidProposal(String ldc) {
		return ldc.chars().anyMatch(c -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z');
	}

	private Collection<LdcInsnNode> collectLdcNodes(Frame<SourceValue> frame) {
		Collection<LdcInsnNode> ldcs = new ArrayList<>();
		for (int i = 0; i < frame.getStackSize(); i++) {
			for (AbstractInsnNode node : frame.getStack(i).insns) {
				if (node instanceof LdcInsnNode) {
					ldcs.add((LdcInsnNode) node);
				}
			}
		}
		return ldcs;
	}

	private static class ClinitCollector extends ClassVisitor {
		private final List<MethodNode> nodes = new ArrayList<>();

		private ClinitCollector() {
			super(Opcodes.ASM7);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if ("<clinit>".equals(name)) {
				MethodNode node = new MethodNode(api, access, name, descriptor, signature, exceptions);
				nodes.add(node);
				return node;
			}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}
}
