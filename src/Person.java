public abstract class Person {
    private String name;
    private String email;

    public Person(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }

    // Every subclass must define its own role
    public abstract String getRole();

    public String getDetails() {
        return name + " (" + getRole() + ") - " + email;
    }
}
