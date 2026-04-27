package gulliver.api;

public interface IResizeableEntity {
    float getSizeMultiplier();

    float getSizeMultiplierRoot();

    void halveSize();

    void doubleSize();

    boolean isTiny();

    boolean isExtraTiny();

    boolean isHuge();

    float getStepHeight();
}
