package net.fabricmc.stitch.util;

import com.google.common.collect.HashBiMap;

import javax.annotation.Nullable;
import java.util.Map;

public class ClassProposalSet {
	private final Map<String, String> fieldProposals = HashBiMap.create();

	public void proposeField(String source, String proposal) {
		if (this.fieldProposals.containsValue(source)) {
			throw new IllegalArgumentException("Duplicate field proposal '" + proposal + "'");
		}
		this.fieldProposals.put(source, proposal);
	}

	@Nullable
	public String getProposal(String source) {
		return this.fieldProposals.get(source);
	}

	public boolean isEmpty() {
		return this.fieldProposals.isEmpty();
	}

    public int getProposalCount() {
        return this.fieldProposals.size();
    }
}
