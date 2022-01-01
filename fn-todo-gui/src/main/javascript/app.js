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
                text-align: center;
                display: flex;
                flex-direction: column;
                align-items: center;
            }
            header {
                font-size: xx-large;
                color: rgba(255, 89, 0, 0.6);
                font-family: serif;
                font-style: italic;
            }
            
            ul {
                list-style-type: none;
                padding: 0;
            }
            
            section {
                width: 50%;
            }
            
            input#description {
                height: 2rem;
                width: 100%;
                padding: 0 1rem;
            }
            input#description::placeholder {
                color: darkgrey;
                font-size: 1.2em;
                font-style: italic;
            }
            
            section.list {
                font-style: italic;
                color: #696969;
                font-size: large;
                margin-top: 1rem;
            }
            li.list_element {
                display: flex;
                margin: 1rem 0 1rem 1rem;
            }
            li.list_element span.description {
                flex-grow: 4;
                text-align: left;
            }
            li.list_element span.action {
                text-align: right;
                font-style: normal;
                padding: 0 0.5rem;
            }
            li.list_element span.action:hover {
                background-color: lightgray;
                cursor: pointer;
            }
            </style>

            <header>todos</header>
            <section class="input">
                <input id="description" type="text" placeholder="enter description of next todo">
            </section>
            <section class="list">
            </section>
        `;

        this._$input_description = this.shadowRoot.querySelector('input#description');
        this._$input_description.onkeyup = (e) => { if (e.key === 'Enter') { this._createNew(this._$input_description.value); } };
        this._$output = this.shadowRoot.querySelector('section.list');
    }

    async _loadList() {
        fetch('/api/todos')
            .then(response => response.json())
            .then(data => this._$output.innerHTML = `<ul>${data.map(e => this._renderListEntry(e)).join("")}</ul>`)
            .catch(err => this._$output.innerHTML = `${err}</p><a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS">JavaScript does not have access to CORS related errors</a> - open the console to inspect the actual issue with this CORS request!!`);
    }

    _renderListEntry(entry) {
        return `
        <li class="list_element" data-id="${entry.id}">
            <span class="description">${entry.description}</span>
            <span class="action">&#128465;</span>
        </li>`;
    }

    _createNew(description) {
        fetch('/api/todos',{ method: 'POST', body: JSON.stringify({ "description": description }) })
            .then(async _ => await this._loadList())
            .then(this._$input_description.value = "");
    }
}

customElements.define('todo-list', TodoList);