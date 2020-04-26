layout (constant_id = 0) const int MAX_POINT_LIGHTS = 32;
layout (constant_id = 1) const int MAX_SPOT_LIGHTS = 32;
layout (constant_id = 2) const int MAX_DIRECTIONAL_LIGHTS = 32;

struct AmbientLight {
    vec3 color;
};

struct PointLight {
    vec3 viewPosition;
    vec3 color;
    float intensity;
    float attenuationConstant;
    float attenuationLinear;
    float attenuationQuadratic;
    int shadowMapIndex;
};

struct DirectionalLight {
    vec3 viewDirection;
    vec3 color;
    float intensity;
    int shadowMapIndex;
};

struct SpotLight {
    vec3 viewPosition;
    float coscutoff;
    vec3 viewDirection;
    vec3 color;
    float intensity;
    float attenuationConstant;
    float attenuationLinear;
    float attenuationQuadratic;
    int shadowMapIndex;
};

layout(binding = 3) uniform LightBuffer {
    mat4 invertedView;
    AmbientLight ambientLight;
    PointLight pointLights[MAX_POINT_LIGHTS];
    SpotLight spotLights[MAX_SPOT_LIGHTS];
    DirectionalLight directionalLights[MAX_DIRECTIONAL_LIGHTS];
    int pointLightCount;
    int spotLightCount;
    int directionalLightCount;
} lights;

// Light types
const int TYPE_POINT = 0;
const int TYPE_DIRECTIONAL = 1;
const int TYPE_SPOT = 2;
