package sexy.lyrics;

public class Lyrics {
    private String lyrics;
    private String title;
    private String artist;
    private boolean status;
    private String errorMessage;
    private Genius.GeniusLookUpResult[] results;

    public Lyrics() {

    }

    public Lyrics(String error) {
        status = false;
        errorMessage = error;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public boolean status() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Genius.GeniusLookUpResult[] getResults() {
        return results;
    }

    public void setResults(Genius.GeniusLookUpResult[] results) {
        this.results = results;
    }

}