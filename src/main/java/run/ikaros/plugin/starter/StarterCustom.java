package run.ikaros.plugin.starter;

import run.ikaros.api.custom.Custom;
import run.ikaros.api.custom.Name;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Custom(group = "starter.ikaros.run", version = "v1alpha1",
        kind = "StarterCustom", singular = "starter", plural = "starters")
public class StarterCustom {
    @Name
    private String title;

    private Integer number;
}
