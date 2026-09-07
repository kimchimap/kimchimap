package kr.kimchimap.origin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ingredient", schema = "app")
public class Ingredient {
  @Id private UUID id;
  private String code;
  private String name;
  private String category;
  private boolean active;

  protected Ingredient() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }
}
