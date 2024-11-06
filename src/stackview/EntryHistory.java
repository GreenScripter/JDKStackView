package stackview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntryHistory {

	private final List<Integer> history = new ArrayList<>();

	private final List<EntryHistory> sources = new ArrayList<>();

	public EntryHistory() {

	}

	public EntryHistory(int source) {
		history.add(source);
	}

	public EntryHistory(int source, List<EntryHistory> sources) {
		history.add(source);
		this.sources.addAll(sources);
	}

	public EntryHistory(int next, EntryHistory other) {
		history.addAll(other.history);
		sources.addAll(other.sources);
		history.add(next);
	}

	public EntryHistory(int next, List<EntryHistory> sources, EntryHistory other) {
		history.addAll(other.history);
		this.sources.addAll(other.sources);
		history.add(next);
		this.sources.addAll(sources);
	}

	public EntryHistory(List<EntryHistory> sources, EntryHistory other) {
		history.addAll(other.history);
		this.sources.addAll(other.sources);
		this.sources.addAll(sources);
	}

	public List<Integer> viewHistory() {
		return Collections.unmodifiableList(history);
	}

	public List<EntryHistory> viewSources() {
		return Collections.unmodifiableList(sources);
	}

}
