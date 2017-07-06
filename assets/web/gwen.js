$.get("status", function(data) {
    applyStatus(data);
})

refreshModels();

$("#projectSave").click(function () {
    $("#projectSaveError").hide();
    $.ajax({
        url: "projectSave",
        data: { clientId: $("#clientId").val(), clientSecret: $("#clientSecret").val() },
        success: function(data) {
            applyStatus(data);
        },
        error: function(data) {
            alert("Couldn't save client ID & sercet. Please try again");
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
            refreshModels();
        },
        error: function(data) {
            alert("Couldn't authorize your account. Please try again.");
            $("#code").val("");
        }
    })
});

$("#modelSave").click(function() {
    var formData = new FormData();
    formData.append("modelName", $("#modelName").val());
    formData.append("modelType", $("#modelType").val());
    formData.append("file", $("#modelFile")[0].files[0]);

    $.ajax({
        url: "modelSave",
        type: "POST",
        data: formData,
        processData: false,
        contentType: false,
        success: function(data) {
            $("#modelName").val("");
            $("#modelFile").val("");
            applyModels(data);
        },
        error: function(data) {
            alert("Couldn't save model");
        }
    })
})

function applyStatus(data) {
    $("#default").hide();

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
            $("#default").show()
        }
    }
}

function refreshModels() {
    $.ajax({
        url: "models",
        success: applyModels,
        error: function(data) {
        }
    });
}

function applyModels(data) {
    $("#modelList").empty();
    $("#modelList").append("<tr><th>Name</th><th>Type</th><th></th></tr>")

    for (var i = 0; i < data.length; i++) {
        var model = data[i];
        var modelHtml = $("<tr><td>" + model.name + "</td><td>" + model.type + "</td><td><button>Delete</button></td></tr>");

        (function() {
            var name = model.name;
            modelHtml.find("button").click(function(el) {
                alert("Deleting " + name);

                $.ajax({
                    url: "modelDelete",
                    data: { name: name },
                    success: applyModels,
                    error: function(data) {
                        alert("Couldn't delete model " + name);
                    }
                });
            })
        })();
        $("#modelList").append(modelHtml);
    }
}