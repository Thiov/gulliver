package gulliver.api;

public interface IResizeableLiving extends IResizeableEntity {
    float getSizePotionMultiplier();

    float getSizeItemMultiplier();

    void setBaseSize(float size);

    void adjustBaseSize(float factor);
}
