package fr.chatcmds;

/** Where the mod writes: public chat (say) or only your own chat window (local). */
interface Out {
    /** Sends a message in the public chat. */
    void say(String message);

    /** Shows a message only to you. */
    void local(String message);
}
