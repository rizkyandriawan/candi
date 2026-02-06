package candi.runtime;

/**
 * Thrown when a requested fragment does not exist on a page.
 */
public class FragmentNotFoundException extends RuntimeException {

    private final String fragmentName;

    public FragmentNotFoundException(String fragmentName) {
        super("Fragment not found: " + fragmentName);
        this.fragmentName = fragmentName;
    }

    public String getFragmentName() {
        return fragmentName;
    }
}
