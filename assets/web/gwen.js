$.get("status", function(data) {
    applyStatus(data);
})

$("#projectSave").click(function () {
    $("#projectSaveError").hide();
    $.ajax({
        url: "projectSave",
        data: { clientId: $("#clientId").val(), clientSecret: $("#clientSecret").val() },
        success: function(data) {
            applyStatus(data);
        },
        error: function(data) {
            $("#projectSaveError").show();
            $("#clientId").val("")
            $("#clientSecret").val("")
        }
    });
});

$("#accountSave").click(function() {
    $.ajax({
        url: "accountSave",
        data: { code: $("#code").val() },
        success: function(data) {
            applyStatus(data);
        },
        error: function(data) {
            $("#accountSaveError").show();
            $("#code").val("");
        }
    })
});

function applyStatus(data) {
    $("#projectSetup").hide()
    $("#projectSaveError").hide();
    $("#account").hide()
    $("#accountSaveError").hide();

    if (data.needsClientId) {
        $("#projectSetup").show()
    } else {
        if (data.needsAuthorization) {
            $("#account").show()
            $("#authorizationUrl").attr("href", data.authorizationUrl);
        } else {
            $("#account").hide()
        }
    }
}