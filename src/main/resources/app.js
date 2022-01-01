class TodoList extends HTMLElement {
    constructor() {
        super();
        this.root = this.attachShadow({mode: 'open'});
    }

    connectedCallback() {
        customElements
            .whenDefined('todo-list')
            .then(() => this.render())
            .then(() => this._loadList());
    }

    render() {
        this.root.innerHTML = `
                <style>
                    :host {
                        display: flex;
                        flex-direction: column;
                        min-width: 640px;
                        max-width: 50%;
                        font-family: Arial;
                    }
                    label { font-size: 0.8rem; }
                    fieldset { display: flex; flex-direction: column; }
                    fieldset > * + * { margin-top: 1rem; } 
                    textarea { font-family: monospace; min-width: 100%; font-size: 1.2rem; }
                    .labeled-field {
                        display: flex;
                        justify-content: space-between;
                    }
                    ul li { list-style-type: none; }                    
                    .labeled-field > input { width: 100%; }
                    .output {                        
                        background: lightgray;
                        padding: 1rem;
                        border: 1px solid darkgray;
                        margin-top: 1rem;
                    }
                    input[type=submit] {
                        margin: 2rem;
                        background: darkblue;
                        color: white;
                        border-radius: 1rem;
                    }
                    hr { width: 100%; margin-top: 2rem; }
                </style>
                
                
                <fieldset>
                    <legend>Adding a new TODO:</legend>
                    <div class="labeled-field">
                        <label for="url">Desc.: </label>
                        <input id="description" type="text">
                    </div>                    
                </fieldset>
                <input type="submit" value="Add"/>
                <hr>
                <div class="output"></div>
            `;

        this._$input_description = this.shadowRoot.querySelector('input#description');
        this._$submit = this.shadowRoot.querySelector('[type=submit]');
        this._$submit.onclick = (e) => this._createNew(this._$input_description.value);
        this._$output = this.shadowRoot.querySelector('.output');
    }

    async _loadList() {
        fetch('/api/todos')
            .then(response => response.json())
            .then(data => this._$output.innerHTML = `<ul>${data.map(e => this._renderListEntry(e)).join("")}</ul>`)
            .catch(err => this._$output.innerHTML = `${err}</p><a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS">JavaScript does not have access to CORS related errors</a> - open the console to inspect the actual issue with this CORS request!!`);
    }

    _renderListEntry(entry) {
        return `
        <li data-id="${entry.id}">                    
            ${(entry.completed) ? `<input id="${entry.id}" type="checkbox" checked>` : `<input id="${entry.id}" type="checkbox">`}
            <label for="${entry.id}">${entry.description}</label>            
        </li>`;
    }

    _createNew(description) {
        fetch('/api/todos',{ method: 'POST', body: JSON.stringify({ "description": description }) })
            .then(async _ => await this._loadList());
    }
}

customElements.define('todo-list', TodoList);