package stackview;

import java.util.ArrayList;
import java.util.List;

public class EntryValue {

	private final EntryType type;

	private final String knownValue;

	public EntryType getType() {
		return type;
	}

	public EntryHistory getSourceHistory() {
		return sourceHistory;
	}

	private final EntryHistory sourceHistory;

	public EntryValue(EntryType type) {
		this(type, (String) null);
	}

	public EntryValue(EntryType type, String knownValue) {
		this.type = type;
		sourceHistory = new EntryHistory(-1);
		this.knownValue = knownValue;
	}

	public EntryValue(EntryType type, int instructionSource) {
		this(type, instructionSource, (String) null);
	}

	public EntryValue(EntryType type, int instructionSource, String knownValue) {
		this.type = type;
		sourceHistory = new EntryHistory(instructionSource);
		this.knownValue = knownValue;

	}

	public EntryValue(EntryType type, int instructionSource, List<EntryValue> parents) {
		this(type, instructionSource, parents, null);
	}

	public EntryValue(EntryType type, int instructionSource, List<EntryValue> parents, String knownValue) {
		this.type = type;
		List<EntryHistory> histories = new ArrayList<>();
		for (var ev : parents) {
			histories.add(ev.sourceHistory);
		}
		sourceHistory = new EntryHistory(instructionSource, histories);
		this.knownValue = knownValue;
	}

	public EntryValue(EntryType type, EntryHistory instructionSource) {
		this(type, instructionSource, null);
	}

	public EntryValue(EntryType type, EntryHistory instructionSource, String knownValue) {
		this.type = type;
		this.sourceHistory = instructionSource;
		this.knownValue = knownValue;
	}

	public String toString() {
		return type + "";
	}

	public EntryValue move(int instructionSource) {
		EntryValue move = new EntryValue(type, new EntryHistory(instructionSource, sourceHistory), knownValue);
		return move;
	}

	public EntryValue known(String known) {
		EntryValue move = new EntryValue(type, sourceHistory, known);
		return move;
	}

	public EntryValue move(EntryType type, int instructionSource) {
		EntryValue move = new EntryValue(type, new EntryHistory(instructionSource, sourceHistory), knownValue);
		return move;
	}

	public String getKnownValue() {
		return knownValue;
	}

}
